package com.miranda.luna.journal.infrastructure.web.dto;

import lombok.Builder;

@Builder
public record JournalStatusResponse(
        String status,
        String version,
        String service,
        long totalGroups,
        long totalStreams,
        long totalEvents,
        boolean dockerConnected
) {
}
