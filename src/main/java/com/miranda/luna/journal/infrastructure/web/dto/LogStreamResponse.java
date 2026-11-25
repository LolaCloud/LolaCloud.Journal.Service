package com.miranda.luna.journal.infrastructure.web.dto;

import com.miranda.luna.journal.domain.model.LogStream;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record LogStreamResponse(
        UUID id,
        String groupName,
        String name,
        String sourceType,
        String sourceTarget,
        Instant lastEventTimestamp,
        Instant createdAt,
        Instant updatedAt
) {
    public static LogStreamResponse from(LogStream stream) {
        return new LogStreamResponse(
                stream.getId(),
                stream.getGroup() != null ? stream.getGroup().getName() : null,
                stream.getName(),
                stream.getSourceType(),
                stream.getSourceTarget(),
                stream.getLastEventTimestamp(),
                stream.getCreatedAt(),
                stream.getUpdatedAt()
        );
    }
}
