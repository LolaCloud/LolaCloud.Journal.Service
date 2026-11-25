package com.miranda.luna.journal.application.service;

import com.miranda.luna.journal.domain.exception.ConflictException;
import com.miranda.luna.journal.domain.exception.ResourceNotFoundException;
import com.miranda.luna.journal.domain.model.LogEvent;
import com.miranda.luna.journal.domain.model.LogGroup;
import com.miranda.luna.journal.domain.model.LogStream;
import com.miranda.luna.journal.domain.repository.LogEventRepository;
import com.miranda.luna.journal.domain.repository.LogGroupRepository;
import com.miranda.luna.journal.domain.repository.LogStreamRepository;
import com.miranda.luna.journal.infrastructure.web.dto.CreateLogGroupRequest;
import com.miranda.luna.journal.infrastructure.web.dto.IngestLogEventRequest;
import com.miranda.luna.journal.infrastructure.web.dto.LogEventResponse;
import com.miranda.luna.journal.infrastructure.web.dto.LogGroupResponse;
import com.miranda.luna.journal.infrastructure.web.dto.LogStreamResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LogService {

    private static final Logger logger = LoggerFactory.getLogger(LogService.class);

    private final LogGroupRepository groupRepository;
    private final LogStreamRepository streamRepository;
    private final LogEventRepository eventRepository;
    private final DockerTelemetryService dockerTelemetryService;
    private final ObjectMapper objectMapper;

    @Value("${lola.journal.retention.default-days:7}")
    private int defaultRetentionDays;

    private final List<TailSubscriber> liveTailSubscribers = new CopyOnWriteArrayList<>();

    public LogService(
            LogGroupRepository groupRepository,
            LogStreamRepository streamRepository,
            LogEventRepository eventRepository,
            DockerTelemetryService dockerTelemetryService,
            ObjectMapper objectMapper
    ) {
        this.groupRepository = groupRepository;
        this.streamRepository = streamRepository;
        this.eventRepository = eventRepository;
        this.dockerTelemetryService = dockerTelemetryService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void autoSeedStandardGroups() {
        seedGroupIfNotPresent("/lola/relate/instances", "Relate Database Instances Logs", defaultRetentionDays);
        seedGroupIfNotPresent("/lola/platform/system", "Lola Cloud Platform Core System Logs", defaultRetentionDays);
    }

    private void seedGroupIfNotPresent(String name, String description, int retentionDays) {
        if (groupRepository.findByName(name).isEmpty()) {
            LogGroup group = LogGroup.builder()
                    .name(name)
                    .description(description)
                    .retentionDays(retentionDays)
                    .build();
            groupRepository.save(group);
            logger.info("Auto-seeded standard log group: {}", name);
        }
    }

    @Transactional(readOnly = true)
    public List<LogGroupResponse> listGroups() {
        return groupRepository.findAll().stream()
                .map(g -> LogGroupResponse.from(g, streamRepository.countByGroupId(g.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public LogGroupResponse getGroup(String name) {
        LogGroup group = findGroupFlexible(name)
                .orElseThrow(() -> new ResourceNotFoundException("Log group not found: " + name));
        long streamCount = streamRepository.countByGroupId(group.getId());
        return LogGroupResponse.from(group, streamCount);
    }

    @Transactional
    public LogGroupResponse createGroup(CreateLogGroupRequest request) {
        String groupName = request.name();
        if (findGroupFlexible(groupName).isPresent()) {
            throw new ConflictException("Log group already exists with name: " + groupName);
        }

        LogGroup group = LogGroup.builder()
                .name(groupName)
                .description(request.description())
                .retentionDays(request.resolvedRetentionDays())
                .build();

        LogGroup saved = groupRepository.save(group);
        return LogGroupResponse.from(saved, 0);
    }

    @Transactional
    public void deleteGroup(String name) {
        LogGroup group = findGroupFlexible(name)
                .orElseThrow(() -> new ResourceNotFoundException("Log group not found: " + name));
        groupRepository.delete(group);
    }

    @Transactional(readOnly = true)
    public List<LogStreamResponse> listStreams(String groupName) {
        LogGroup group = findGroupFlexible(groupName)
                .orElseThrow(() -> new ResourceNotFoundException("Log group not found: " + groupName));

        return streamRepository.findByGroupId(group.getId()).stream()
                .map(LogStreamResponse::from)
                .toList();
    }

    public Optional<LogGroup> findGroupFlexible(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Optional<LogGroup> direct = groupRepository.findByName(name);
        if (direct.isPresent()) {
            return direct;
        }
        if (!name.startsWith("/")) {
            Optional<LogGroup> withSlash = groupRepository.findByName("/" + name);
            if (withSlash.isPresent()) {
                return withSlash;
            }
        } else {
            Optional<LogGroup> withoutSlash = groupRepository.findByName(name.substring(1));
            if (withoutSlash.isPresent()) {
                return withoutSlash;
            }
        }
        return Optional.empty();
    }

    @Transactional
    public LogEventResponse ingestEvent(String groupName, String streamName, IngestLogEventRequest request) {
        LogGroup group = findGroupFlexible(groupName)
                .orElseGet(() -> {
                    LogGroup newGroup = LogGroup.builder()
                            .name(groupName)
                            .description("Auto-created log group")
                            .retentionDays(defaultRetentionDays)
                            .build();
                    return groupRepository.save(newGroup);
                });

        LogStream stream = streamRepository.findByGroupAndName(group, streamName)
                .orElseGet(() -> {
                    LogStream newStream = LogStream.builder()
                            .group(group)
                            .name(streamName)
                            .sourceType("DOCKER_CONTAINER")
                            .sourceTarget(streamName)
                            .build();
                    return streamRepository.save(newStream);
                });

        Instant eventTime = request.resolvedTimestamp();
        stream.setLastEventTimestamp(eventTime);
        streamRepository.save(stream);

        LogEvent event = LogEvent.builder()
                .stream(stream)
                .timestamp(eventTime)
                .level(request.resolvedLevel())
                .message(request.message())
                .attributes(request.attributes())
                .build();

        LogEvent saved = eventRepository.save(event);
        LogEventResponse response = LogEventResponse.from(saved);

        broadcastLiveTailEvent(group.getName(), streamName, response);

        return response;
    }

    @Transactional(readOnly = true)
    public Page<LogEventResponse> queryEvents(
            String groupName,
            String streamName,
            Instant since,
            Instant until,
            String query,
            Pageable pageable
    ) {
        LogGroup group = findGroupFlexible(groupName)
                .orElseThrow(() -> new ResourceNotFoundException("Log group not found: " + groupName));

        if (streamName != null && !streamName.isBlank()) {
            LogStream stream = streamRepository.findByGroupAndName(group, streamName)
                    .orElseThrow(() -> new ResourceNotFoundException("Log stream not found: " + streamName + " in group " + groupName));
            return eventRepository.findEvents(stream.getId(), since, until, query, pageable)
                    .map(LogEventResponse::from);
        }

        return eventRepository.findGroupEvents(group.getName(), since, until, query, pageable)
                .map(LogEventResponse::from);
    }

    public SseEmitter createLiveTail(String group, String stream, String container) {
        // 5 minute timeout for SSE stream
        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicBoolean active = new AtomicBoolean(true);

        emitter.onCompletion(() -> {
            active.set(false);
            removeSubscriber(emitter);
        });
        emitter.onTimeout(() -> {
            active.set(false);
            removeSubscriber(emitter);
            emitter.complete();
        });
        emitter.onError(e -> {
            active.set(false);
            removeSubscriber(emitter);
            emitter.complete();
        });

        // Mode 1: Direct Docker container live log streaming
        if (container != null && !container.isBlank()) {
            CompletableFuture.runAsync(() -> {
                try {
                    // Send initial connection event
                    emitter.send(SseEmitter.event()
                            .name("connected")
                            .data(Map.of("message", "Attached live tail for container: " + container)));

                    dockerTelemetryService.streamContainerLogs(container, line -> {
                        if (!active.get()) {
                            return;
                        }
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("log")
                                    .data(Map.of("container", container, "line", line)));
                        } catch (Exception e) {
                            active.set(false);
                            emitter.complete();
                        }
                    }, active);
                } catch (Exception e) {
                    logger.debug("Error streaming container logs for {}: {}", container, e.getMessage());
                    emitter.complete();
                }
            });
            return emitter;
        }

        // Mode 2: Log group / stream live tail
        TailSubscriber subscriber = new TailSubscriber(group, stream, emitter, active);
        liveTailSubscribers.add(subscriber);

        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("connected")
                        .data(Map.of("message", "Subscribed to live tail for group: " + (group != null ? group : "*")
                                + (stream != null ? ", stream: " + stream : ""))));

                // Send recent history if stream is known
                if (group != null && stream != null) {
                    streamRepository.findByGroupNameAndName(group, stream).ifPresent(s -> {
                        List<LogEvent> recent = eventRepository.findTop50ByStreamIdOrderByTimestampDesc(s.getId());
                        // Send from oldest to newest
                        for (int i = recent.size() - 1; i >= 0; i--) {
                            if (!active.get()) break;
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("log")
                                        .data(LogEventResponse.from(recent.get(i))));
                            } catch (Exception ignored) {
                                break;
                            }
                        }
                    });
                }
            } catch (Exception e) {
                logger.debug("Error initializing SSE emitter: {}", e.getMessage());
            }
        });

        return emitter;
    }

    private void broadcastLiveTailEvent(String groupName, String streamName, LogEventResponse event) {
        for (TailSubscriber sub : liveTailSubscribers) {
            if (!sub.active.get()) {
                continue;
            }
            boolean matchesGroup = sub.group == null || sub.group.equalsIgnoreCase(groupName);
            boolean matchesStream = sub.stream == null || sub.stream.equalsIgnoreCase(streamName);
            if (matchesGroup && matchesStream) {
                try {
                    sub.emitter.send(SseEmitter.event().name("log").data(event));
                } catch (Exception e) {
                    sub.active.set(false);
                    removeSubscriber(sub.emitter);
                }
            }
        }
    }

    private void removeSubscriber(SseEmitter emitter) {
        liveTailSubscribers.removeIf(s -> s.emitter.equals(emitter));
    }

    private record TailSubscriber(
            String group,
            String stream,
            SseEmitter emitter,
            AtomicBoolean active
    ) {
    }
}
