package com.miranda.luna.journal.application.service;

import com.miranda.luna.journal.domain.model.ContainerStats;
import com.miranda.luna.journal.domain.model.FleetSummary;
import com.miranda.luna.journal.domain.model.InstanceMetric;
import com.miranda.luna.journal.domain.model.MetricPoint;
import com.miranda.luna.journal.domain.repository.InstanceMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    private final InstanceMetricRepository metricRepository;
    private final DockerTelemetryService dockerTelemetryService;

    @Value("${lola.journal.docker.enabled:true}")
    private boolean dockerEnabled;

    public MetricsService(
            InstanceMetricRepository metricRepository,
            DockerTelemetryService dockerTelemetryService
    ) {
        this.metricRepository = metricRepository;
        this.dockerTelemetryService = dockerTelemetryService;
    }

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void collectDockerMetrics() {
        if (!dockerEnabled) {
            return;
        }

        try {
            List<String> containers = dockerTelemetryService.listLolaContainers();
            if (containers.isEmpty()) {
                return;
            }

            Instant now = Instant.now();
            List<InstanceMetric> metricsToSave = new ArrayList<>();

            for (String containerName : containers) {
                ContainerStats stats = dockerTelemetryService.fetchContainerStats(containerName);
                if (stats != null && !"STOPPED".equals(stats.status()) && !"UNKNOWN".equals(stats.status())) {
                    String engine = resolveEngine(containerName);

                    InstanceMetric metric = InstanceMetric.builder()
                            .instanceName(containerName)
                            .engine(engine)
                            .timestamp(now)
                            .cpuPercent(stats.cpuPercent())
                            .memoryUsedBytes(stats.memoryUsedBytes())
                            .memoryLimitBytes(stats.memoryLimitBytes())
                            .diskUsedBytes(0L)
                            .activeConnections(0)
                            .status(stats.status())
                            .build();

                    metricsToSave.add(metric);
                }
            }

            if (!metricsToSave.isEmpty()) {
                metricRepository.saveAll(metricsToSave);
                logger.debug("Persisted telemetry metrics snapshot for {} containers", metricsToSave.size());
            }
        } catch (Exception e) {
            logger.warn("Error during scheduled docker metrics collection: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<MetricPoint> getMetricsHistory(String instanceName, Instant from, Instant to, Integer intervalSeconds) {
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(1, ChronoUnit.HOURS);

        List<InstanceMetric> rawList = metricRepository
                .findByInstanceNameAndTimestampBetweenOrderByTimestampAsc(instanceName, effectiveFrom, effectiveTo);

        if (rawList.isEmpty()) {
            // Fallback: If no history is in the DB yet, check live stats and provide current point
            ContainerStats live = dockerTelemetryService.fetchContainerStats(instanceName);
            if (live != null && !"STOPPED".equals(live.status())) {
                return List.of(MetricPoint.builder()
                        .timestamp(Instant.now())
                        .cpuPercent(live.cpuPercent())
                        .memoryUsedBytes(live.memoryUsedBytes())
                        .memoryLimitBytes(live.memoryLimitBytes())
                        .memoryPercent(live.memoryPercent())
                        .diskUsedBytes(0L)
                        .activeConnections(0)
                        .status(live.status())
                        .build());
            }
            return List.of();
        }

        if (intervalSeconds == null || intervalSeconds <= 0 || rawList.size() <= 100) {
            return rawList.stream().map(this::toMetricPoint).toList();
        }

        // Downsample / bucket into intervalSeconds
        long intervalMs = intervalSeconds * 1000L;
        Map<Long, List<InstanceMetric>> buckets = rawList.stream().collect(
                Collectors.groupingBy(m -> (m.getTimestamp().toEpochMilli() / intervalMs) * intervalMs)
        );

        return buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Instant bucketTime = Instant.ofEpochMilli(entry.getKey());
                    List<InstanceMetric> items = entry.getValue();
                    double avgCpu = items.stream().mapToDouble(InstanceMetric::getCpuPercent).average().orElse(0.0);
                    long avgMemUsed = (long) items.stream().mapToLong(InstanceMetric::getMemoryUsedBytes).average().orElse(0L);
                    long maxMemLimit = items.stream().mapToLong(InstanceMetric::getMemoryLimitBytes).max().orElse(0L);
                    double memPercent = maxMemLimit > 0 ? (avgMemUsed * 100.0 / maxMemLimit) : 0.0;
                    String status = items.get(items.size() - 1).getStatus();

                    return MetricPoint.builder()
                            .timestamp(bucketTime)
                            .cpuPercent(Math.round(avgCpu * 100.0) / 100.0)
                            .memoryUsedBytes(avgMemUsed)
                            .memoryLimitBytes(maxMemLimit)
                            .memoryPercent(Math.round(memPercent * 100.0) / 100.0)
                            .diskUsedBytes(0L)
                            .activeConnections(0)
                            .status(status)
                            .build();
                })
                .toList();
    }

    public ContainerStats getLiveStats(String instanceName) {
        return dockerTelemetryService.fetchContainerStats(instanceName);
    }

    public FleetSummary getFleetMetricsSummary() {
        List<String> containers = dockerTelemetryService.listLolaContainers();
        List<ContainerStats> statsList = containers.stream()
                .map(dockerTelemetryService::fetchContainerStats)
                .toList();

        int healthy = 0;
        int warning = 0;
        int critical = 0;
        double sumCpu = 0.0;
        long totalMemUsed = 0L;
        long totalMemLimit = 0L;

        for (ContainerStats s : statsList) {
            switch (s.status()) {
                case "HEALTHY" -> healthy++;
                case "WARNING" -> warning++;
                case "CRITICAL" -> critical++;
                default -> healthy++;
            }
            sumCpu += s.cpuPercent();
            totalMemUsed += s.memoryUsedBytes();
            totalMemLimit += s.memoryLimitBytes();
        }

        double avgCpu = statsList.isEmpty() ? 0.0 : Math.round((sumCpu / statsList.size()) * 100.0) / 100.0;

        return FleetSummary.builder()
                .totalContainers(statsList.size())
                .healthyContainers(healthy)
                .warningContainers(warning)
                .criticalContainers(critical)
                .avgCpuPercent(avgCpu)
                .totalMemoryUsedBytes(totalMemUsed)
                .totalMemoryLimitBytes(totalMemLimit)
                .containers(statsList)
                .build();
    }

    @Transactional
    public int deleteMetricsForInstance(String instanceName) {
        if (instanceName == null || instanceName.isBlank()) {
            return 0;
        }
        String altName = instanceName.startsWith("lola-relate-db-")
                ? instanceName.substring("lola-relate-db-".length())
                : "lola-relate-db-" + instanceName;
        int deleted = metricRepository.deleteByInstanceNameOrAlt(instanceName, altName);
        logger.info("Purged {} metric rows for instance '{}' (alt: '{}')", deleted, instanceName, altName);
        return deleted;
    }

    private MetricPoint toMetricPoint(InstanceMetric m) {
        double memPercent = m.getMemoryLimitBytes() > 0
                ? (m.getMemoryUsedBytes() * 100.0 / m.getMemoryLimitBytes())
                : 0.0;

        return MetricPoint.builder()
                .timestamp(m.getTimestamp())
                .cpuPercent(m.getCpuPercent())
                .memoryUsedBytes(m.getMemoryUsedBytes())
                .memoryLimitBytes(m.getMemoryLimitBytes())
                .memoryPercent(Math.round(memPercent * 100.0) / 100.0)
                .diskUsedBytes(m.getDiskUsedBytes())
                .activeConnections(m.getActiveConnections())
                .status(m.getStatus())
                .build();
    }

    private String resolveEngine(String containerName) {
        String lower = containerName.toLowerCase();
        if (lower.contains("postgres") || lower.contains("pg")) {
            return "postgresql";
        }
        if (lower.contains("mysql")) {
            return "mysql";
        }
        if (lower.contains("sqlserver") || lower.contains("mssql")) {
            return "sqlserver";
        }
        if (lower.contains("traefik")) {
            return "traefik";
        }
        if (lower.contains("minio") || lower.contains("store")) {
            return "minio";
        }
        return "system";
    }
}
