package com.miranda.luna.journal.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "log_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stream_id", nullable = false)
    private LogStream stream;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String level = "INFO";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (level == null || level.isBlank()) {
            level = "INFO";
        }
    }
}
