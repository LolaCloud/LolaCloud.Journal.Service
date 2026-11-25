package com.miranda.luna.journal.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record ContainerStats(
        String containerId,
        String name,
        double cpuPercent,
        long memoryUsedBytes,
        long memoryLimitBytes,
        double memoryPercent,
        String netIO,
        String blockIO,
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

    @JsonProperty("containerName")
    public String containerName() {
        return name;
    }

    @JsonProperty("netIo")
    public String netIo() {
        return netIO;
    }

    @JsonProperty("blockIo")
    public String blockIo() {
        return blockIO;
    }

    public static ContainerStats defaultFor(String name, String status) {
        return new ContainerStats(
                "",
                name,
                0.0,
                0L,
                0L,
                0.0,
                "0B / 0B",
                "0B / 0B",
                status != null ? status : "UNKNOWN"
        );
    }
}
