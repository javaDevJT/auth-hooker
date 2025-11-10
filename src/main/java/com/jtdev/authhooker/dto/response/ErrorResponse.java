package com.jtdev.authhooker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standard error response format for API errors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String error;
    private String message;
    private String path;
    private LocalDateTime timestamp;
    private String correlationId;
    
    /**
     * Create an error response
     */
    public static ErrorResponse of(String error, String message, String path) {
        return ErrorResponse.builder()
                .error(error)
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create an error response with correlation ID
     */
    public static ErrorResponse of(String error, String message, String path, String correlationId) {
        return ErrorResponse.builder()
                .error(error)
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
    }
}
