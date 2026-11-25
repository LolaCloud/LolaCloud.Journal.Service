package com.miranda.luna.journal.infrastructure.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/health/liveness")
    public Map<String, String> liveness() {
        return Map.of("status", "UP");
    }

    @GetMapping("/health/readiness")
    public Map<String, String> readiness() {
        return Map.of("status", "UP");
    }
}
