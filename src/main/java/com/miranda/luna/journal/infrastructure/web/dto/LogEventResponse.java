package com.miranda.luna.journal.infrastructure.web.dto;

import com.miranda.luna.journal.domain.model.LogEvent;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record LogEventResponse(
        UUID id,
        String streamName,
        Instant timestamp,
        String level,
        String message,
        Map<String, Object> attributes
) {
    public static LogEventResponse from(LogEvent event) {
        return new LogEventResponse(
                event.getId(),
                event.getStream() != null ? event.getStream().getName() : null,
                event.getTimestamp(),
                event.getLevel(),
                event.getMessage(),
                event.getAttributes()
        );
    }
}
