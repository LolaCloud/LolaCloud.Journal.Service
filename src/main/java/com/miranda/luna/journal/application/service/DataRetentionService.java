package com.miranda.luna.journal.application.service;

import com.miranda.luna.journal.domain.model.LogGroup;
import com.miranda.luna.journal.domain.repository.InstanceMetricRepository;
import com.miranda.luna.journal.domain.repository.LogEventRepository;
import com.miranda.luna.journal.domain.repository.LogGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DataRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(DataRetentionService.class);

    private final LogGroupRepository groupRepository;
    private final LogEventRepository eventRepository;
    private final InstanceMetricRepository metricRepository;

    @Value("${lola.journal.retention.metrics-days:7}")
    private int metricsRetentionDays = 7;

    public DataRetentionService(
            LogGroupRepository groupRepository,
            LogEventRepository eventRepository,
            InstanceMetricRepository metricRepository
    ) {
        this.groupRepository = groupRepository;
        this.eventRepository = eventRepository;
        this.metricRepository = metricRepository;
    }

    @Scheduled(cron = "${lola.journal.retention.cron:0 0 */6 * * *}")
    @Transactional
    public RetentionExecutionResult purgeExpiredData() {
        logger.info("Executing scheduled data retention purge job...");
        Instant now = Instant.now();
        int totalEventsPurged = 0;

        List<LogGroup> groups = groupRepository.findAll();
        for (LogGroup group : groups) {
            int retention = group.getRetentionDays() > 0 ? group.getRetentionDays() : 7;
            Instant cutoff = now.minus(retention, ChronoUnit.DAYS);
            int purged = eventRepository.deleteByGroupAndTimestampBefore(group.getId(), cutoff);
            totalEventsPurged += purged;
            if (purged > 0) {
                logger.info("Purged {} expired log events for group '{}' older than {}", purged, group.getName(), cutoff);
            }
        }

        Instant metricsCutoff = now.minus(metricsRetentionDays, ChronoUnit.DAYS);
        int totalMetricsPurged = metricRepository.deleteByTimestampBefore(metricsCutoff);
        if (totalMetricsPurged > 0) {
            logger.info("Purged {} expired metrics records older than {}", totalMetricsPurged, metricsCutoff);
        }

        logger.info("Data retention complete. Total events purged: {}, total metrics purged: {}",
                totalEventsPurged, totalMetricsPurged);

        return new RetentionExecutionResult(totalEventsPurged, totalMetricsPurged, now);
    }

    public record RetentionExecutionResult(int eventsPurged, int metricsPurged, Instant executionTime) {
    }
}
