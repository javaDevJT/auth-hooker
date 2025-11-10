package com.jtdev.authhooker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of testing a provider connection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResult {
    
    private boolean success;
    private String message;
    private Map<String, Object> details;
    
    @Builder.Default
    private LocalDateTime testedAt = LocalDateTime.now();
    
    public static ConnectionTestResult success(String message) {
        return ConnectionTestResult.builder()
                .success(true)
                .message(message)
                .build();
    }
    
    public static ConnectionTestResult failure(String message, Map<String, Object> details) {
        return ConnectionTestResult.builder()
                .success(false)
                .message(message)
                .details(details)
                .build();
    }
}
