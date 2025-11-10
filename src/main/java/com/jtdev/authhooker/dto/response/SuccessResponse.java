package com.jtdev.authhooker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Simple success response for operations that don't return data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuccessResponse {
    private String message;
    private LocalDateTime timestamp;
    
    /**
     * Create a success response
     */
    public static SuccessResponse of(String message) {
        return SuccessResponse.builder()
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
