package com.miranda.luna.journal.infrastructure.web.controller;

import com.miranda.luna.journal.application.service.LogService;
import com.miranda.luna.journal.infrastructure.web.dto.CreateLogGroupRequest;
import com.miranda.luna.journal.infrastructure.web.dto.IngestLogEventRequest;
import com.miranda.luna.journal.infrastructure.web.dto.LogEventResponse;
import com.miranda.luna.journal.infrastructure.web.dto.LogGroupResponse;
import com.miranda.luna.journal.infrastructure.web.dto.LogStreamResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/journal/groups")
public class LogGroupController {

    private final LogService logService;

    public LogGroupController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public List<LogGroupResponse> listGroups() {
        return logService.listGroups();
    }

    @GetMapping("/{*subpath}")
    public Object dispatchGetSubpath(
            @PathVariable String subpath,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        String cleaned = resolveSubpath(subpath);

        // 1. Check if it's querying events: .../streams/{streamName}/events
        int streamsIdx = cleaned.indexOf("/streams/");
        if (streamsIdx > 0 && cleaned.endsWith("/events")) {
            String groupName = cleaned.substring(0, streamsIdx);
            String streamName = cleaned.substring(streamsIdx + "/streams/".length(), cleaned.length() - "/events".length());
            int effectiveSize = Math.min(Math.max(1, size), 500);
            return logService.queryEvents(groupName, streamName, since, until, query,
                    PageRequest.of(page, effectiveSize, Sort.by(Sort.Direction.DESC, "timestamp")));
        }

        // 2. Check if it's listing streams: .../streams or .../streams/
        if (cleaned.endsWith("/streams") || cleaned.endsWith("/streams/")) {
            String groupName = cleaned.substring(0, cleaned.lastIndexOf("/streams"));
            return logService.listStreams(groupName);
        }

        // 3. Otherwise it's a log group details request
        return logService.getGroup(cleaned);
    }

    @PostMapping
    public ResponseEntity<LogGroupResponse> createGroup(@Valid @RequestBody CreateLogGroupRequest request) {
        LogGroupResponse response = logService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{*subpath}")
    public ResponseEntity<?> dispatchPostSubpath(
            @PathVariable String subpath,
            @Valid @RequestBody IngestLogEventRequest request
    ) {
        String cleaned = resolveSubpath(subpath);
        int streamsIdx = cleaned.indexOf("/streams/");
        if (streamsIdx > 0 && cleaned.endsWith("/events")) {
            String groupName = cleaned.substring(0, streamsIdx);
            String streamName = cleaned.substring(streamsIdx + "/streams/".length(), cleaned.length() - "/events".length());
            LogEventResponse response = logService.ingestEvent(groupName, streamName, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        throw new IllegalArgumentException("Unsupported POST endpoint under group subpath: " + subpath);
    }

    @DeleteMapping("/{*groupName}")
    public Map<String, Object> deleteGroup(@PathVariable String groupName) {
        String resolved = resolveSubpath(groupName);
        logService.deleteGroup(resolved);
        return Map.of("message", "Log group deleted successfully", "name", resolved);
    }

    private String resolveSubpath(String subpath) {
        if (subpath == null) {
            return "";
        }
        String trimmed = subpath.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
