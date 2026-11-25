package com.miranda.luna.journal.infrastructure.web.controller;

import com.miranda.luna.journal.application.service.JournalStatusService;
import com.miranda.luna.journal.infrastructure.web.dto.JournalStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journal")
public class JournalStatusController {

    private final JournalStatusService statusService;

    public JournalStatusController(JournalStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public JournalStatusResponse getStatus() {
        return statusService.getStatus();
    }
}
