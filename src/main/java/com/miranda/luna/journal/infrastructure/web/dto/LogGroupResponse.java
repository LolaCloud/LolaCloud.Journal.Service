package com.miranda.luna.journal.infrastructure.web.dto;

import com.miranda.luna.journal.domain.model.LogGroup;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record LogGroupResponse(
        UUID id,
        String name,
        String description,
        int retentionDays,
        long streamCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static LogGroupResponse from(LogGroup group, long streamCount) {
        return new LogGroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getRetentionDays(),
                streamCount,
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }
}
