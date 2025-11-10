package com.jtdev.authhooker.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health check endpoint
 */
@RestController
@RequestMapping("/health")
public class HealthController {
    
    @Value("${spring.application.name:auth-hooker}")
    private String applicationName;
    
    @Value("${server.port:8080}")
    private String serverPort;
    
    /**
     * Simple health check endpoint (public)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "application", applicationName,
                "port", serverPort,
                "timestamp", LocalDateTime.now(),
                "version", "1.0.0"
        ));
    }
}
