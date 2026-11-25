package com.miranda.luna.journal.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;

@Builder
public record MetricPoint(
        Instant timestamp,
        double cpuPercent,
        long memoryUsedBytes,
        long memoryLimitBytes,
        double memoryPercent,
        long diskUsedBytes,
        int activeConnections,
        String status
) {
    @JsonProperty("memoryUsedMb")
    public double memoryUsedMb() {
        return memoryUsedBytes > 0 ? Math.round((memoryUsedBytes / (1024.0 * 1024.0)) * 100.0) / 100.0 : 0.0;
    }

    @JsonProperty("memoryLimitMb")
    public double memoryLimitMb() {
        return memoryLimitBytes > 0 ? Math.round((memoryLimitBytes / (1024.0 * 1024.0)) * 100.0) / 100.0 : 0.0;
    }

    @JsonProperty("diskUsedMb")
    public double diskUsedMb() {
        return diskUsedBytes > 0 ? Math.round((diskUsedBytes / (1024.0 * 1024.0)) * 100.0) / 100.0 : 0.0;
    }
}
