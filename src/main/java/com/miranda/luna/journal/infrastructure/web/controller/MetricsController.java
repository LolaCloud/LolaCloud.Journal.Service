package com.miranda.luna.journal.infrastructure.web.controller;

import com.miranda.luna.journal.application.service.MetricsService;
import com.miranda.luna.journal.domain.model.ContainerStats;
import com.miranda.luna.journal.domain.model.FleetSummary;
import com.miranda.luna.journal.domain.model.MetricPoint;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/journal/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/instance/{name}")
    public List<MetricPoint> getInstanceMetrics(
            @PathVariable String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer interval
    ) {
        return metricsService.getMetricsHistory(name, from, to, interval);
    }

    @GetMapping("/live/{name}")
    public ContainerStats getLiveStats(@PathVariable String name) {
        return metricsService.getLiveStats(name);
    }

    @GetMapping("/fleet")
    public FleetSummary getFleetSummary() {
        return metricsService.getFleetMetricsSummary();
    }

    @DeleteMapping("/instance/{name}")
    public Map<String, Object> deleteInstanceMetrics(@PathVariable String name) {
        int count = metricsService.deleteMetricsForInstance(name);
        return Map.of("success", true, "deletedCount", count, "instance", name);
    }
}
