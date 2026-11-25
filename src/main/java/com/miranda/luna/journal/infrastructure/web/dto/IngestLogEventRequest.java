package com.miranda.luna.journal.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;

public record IngestLogEventRequest(
        String level,

        @NotBlank(message = "Log message cannot be empty")
        String message,

        Map<String, Object> attributes,

        Instant timestamp
) {
    public String resolvedLevel() {
        return (level != null && !level.isBlank()) ? level.toUpperCase() : "INFO";
    }

    public Instant resolvedTimestamp() {
        return timestamp != null ? timestamp : Instant.now();
    }
}
