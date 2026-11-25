package com.miranda.luna.journal.infrastructure.web.controller;

import com.miranda.luna.journal.application.service.LogService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/journal")
public class LiveTailController {

    private final LogService logService;

    public LiveTailController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping(value = "/tail", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter tailLogs(
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String stream,
            @RequestParam(required = false) String container
    ) {
        return logService.createLiveTail(group, stream, container);
    }
}
