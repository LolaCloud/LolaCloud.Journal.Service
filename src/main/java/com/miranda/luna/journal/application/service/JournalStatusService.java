package com.miranda.luna.journal.application.service;

import com.miranda.luna.journal.domain.repository.LogEventRepository;
import com.miranda.luna.journal.domain.repository.LogGroupRepository;
import com.miranda.luna.journal.domain.repository.LogStreamRepository;
import com.miranda.luna.journal.infrastructure.web.dto.JournalStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JournalStatusService {

    private final LogGroupRepository groupRepository;
    private final LogStreamRepository streamRepository;
    private final LogEventRepository eventRepository;
    private final DockerTelemetryService dockerTelemetryService;

    @Value("${spring.application.name:journal}")
    private String applicationName;

    public JournalStatusService(
            LogGroupRepository groupRepository,
            LogStreamRepository streamRepository,
            LogEventRepository eventRepository,
            DockerTelemetryService dockerTelemetryService
    ) {
        this.groupRepository = groupRepository;
        this.streamRepository = streamRepository;
        this.eventRepository = eventRepository;
        this.dockerTelemetryService = dockerTelemetryService;
    }

    @Transactional(readOnly = true)
    public JournalStatusResponse getStatus() {
        return JournalStatusResponse.builder()
                .status("UP")
                .version("0.0.1-SNAPSHOT")
                .service("journal")
                .totalGroups(groupRepository.count())
                .totalStreams(streamRepository.count())
                .totalEvents(eventRepository.count())
                .dockerConnected(dockerTelemetryService.isDockerConnected())
                .build();
    }
}
