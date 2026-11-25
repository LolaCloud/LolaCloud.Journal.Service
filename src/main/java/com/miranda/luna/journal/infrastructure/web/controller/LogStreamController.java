package com.miranda.luna.journal.infrastructure.web.controller;

import com.miranda.luna.journal.application.service.LogService;
import com.miranda.luna.journal.infrastructure.web.dto.IngestLogEventRequest;
import com.miranda.luna.journal.infrastructure.web.dto.LogEventResponse;
import com.miranda.luna.journal.infrastructure.web.dto.LogStreamResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class LogStreamController {

    private final LogService logService;

    public LogStreamController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping("/api/v1/journal/groups/{groupName}/streams")
    public List<LogStreamResponse> listStreams(@PathVariable String groupName) {
        return logService.listStreams(groupName);
    }

    @GetMapping("/api/v1/journal/streams")
    public List<LogStreamResponse> listStreamsQuery(@RequestParam("group") String groupName) {
        return logService.listStreams(groupName);
    }

    @GetMapping("/api/v1/journal/groups/{groupName}/streams/{streamName}/events")
    public Page<LogEventResponse> queryStreamEvents(
            @PathVariable String groupName,
            @PathVariable String streamName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        int effectiveSize = Math.min(Math.max(1, size), 500);
        return logService.queryEvents(groupName, streamName, since, until, query,
                PageRequest.of(page, effectiveSize, Sort.by(Sort.Direction.DESC, "timestamp")));
    }

    @GetMapping("/api/v1/journal/events")
    public Page<LogEventResponse> queryEvents(
            @RequestParam("group") String groupName,
            @RequestParam(value = "stream", required = false) String streamName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        int effectiveSize = Math.min(Math.max(1, size), 500);
        return logService.queryEvents(groupName, streamName, since, until, query,
                PageRequest.of(page, effectiveSize, Sort.by(Sort.Direction.DESC, "timestamp")));
    }

    @PostMapping("/api/v1/journal/groups/{groupName}/streams/{streamName}/events")
    public ResponseEntity<LogEventResponse> ingestEvent(
            @PathVariable String groupName,
            @PathVariable String streamName,
            @Valid @RequestBody IngestLogEventRequest request
    ) {
        LogEventResponse response = logService.ingestEvent(groupName, streamName, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/v1/journal/events")
    public ResponseEntity<LogEventResponse> ingestEventQuery(
            @RequestParam("group") String groupName,
            @RequestParam("stream") String streamName,
            @Valid @RequestBody IngestLogEventRequest request
    ) {
        LogEventResponse response = logService.ingestEvent(groupName, streamName, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
