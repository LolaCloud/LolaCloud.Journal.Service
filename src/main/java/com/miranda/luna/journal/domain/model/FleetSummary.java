package com.miranda.luna.journal.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record FleetSummary(
        int totalContainers,
        int healthyContainers,
        int warningContainers,
        int criticalContainers,
        double avgCpuPercent,
        long totalMemoryUsedBytes,
        long totalMemoryLimitBytes,
        List<ContainerStats> containers
) {
    @JsonProperty("totalMemoryUsedMb")
    public double totalMemoryUsedMb() {
        return totalMemoryUsedBytes > 0 ? Math.round((totalMemoryUsedBytes / (1024.0 * 1024.0)) * 100.0) / 100.0 : 0.0;
    }

    @JsonProperty("totalMemoryLimitMb")
    public double totalMemoryLimitMb() {
        return totalMemoryLimitBytes > 0 ? Math.round((totalMemoryLimitBytes / (1024.0 * 1024.0)) * 100.0) / 100.0 : 0.0;
    }
}
