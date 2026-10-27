package com.blooddonation.spring.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Real-world: load-balancers, uptime monitors and deploys need a dependency-free
 * health probe that never touches the DB (DB health is covered by Actuator).
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "blood-donation-spring",
                "time", LocalDateTime.now().toString()));
    }
}
