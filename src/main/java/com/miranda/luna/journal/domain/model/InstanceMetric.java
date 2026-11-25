package com.miranda.luna.journal.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instance_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstanceMetric {

    @Id
    private UUID id;

    @Column(name = "instance_id")
    private UUID instanceId;

    @Column(name = "instance_name", nullable = false, length = 100)
    private String instanceName;

    @Column(length = 50)
    private String engine;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "cpu_percent", nullable = false)
    @Builder.Default
    private double cpuPercent = 0.0;

    @Column(name = "memory_used_bytes", nullable = false)
    @Builder.Default
    private long memoryUsedBytes = 0L;

    @Column(name = "memory_limit_bytes", nullable = false)
    @Builder.Default
    private long memoryLimitBytes = 0L;

    @Column(name = "disk_used_bytes", nullable = false)
    @Builder.Default
    private long diskUsedBytes = 0L;

    @Column(name = "active_connections", nullable = false)
    @Builder.Default
    private int activeConnections = 0;

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String status = "HEALTHY";

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (status == null || status.isBlank()) {
            status = "HEALTHY";
        }
    }
}
