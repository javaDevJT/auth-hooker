package com.jtdev.authhooker.service;

import com.jtdev.authhooker.domain.ClaimMapping;
import com.jtdev.authhooker.domain.Provider;
import com.jtdev.authhooker.dto.ClaimMappingRequest;
import com.jtdev.authhooker.exception.ResourceNotFoundException;
import com.jtdev.authhooker.exception.ValidationException;
import com.jtdev.authhooker.repository.ClaimMappingRepository;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing claim mappings and transformations
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ClaimMappingService {
    
    private final ClaimMappingRepository claimMappingRepository;
    private final ProviderService providerService;
    
    /**
     * Create a new claim mapping for a provider
     */
    public ClaimMapping createMapping(UUID providerId, ClaimMappingRequest request) {
        log.info("Creating claim mapping for provider {}: {}", providerId, request.getName());
        
        // Validate provider exists
        Provider provider = providerService.getProviderById(providerId);
        
        // Validate source path is valid JSONPath
        validateJsonPath(request.getSourcePath());
        
        ClaimMapping mapping = ClaimMapping.builder()
                .provider(provider)
                .name(request.getName())
                .description(request.getDescription())
                .sourcePath(request.getSourcePath())
                .targetField(request.getTargetField())
                .transform(request.getTransform() != null ? request.getTransform() : Map.of())
                .priority(request.getPriority())
                .isActive(request.getIsActive())
                .build();
        
        mapping = claimMappingRepository.save(mapping);
        log.info("Claim mapping created: {} (id={})", mapping.getName(), mapping.getId());
        
        return mapping;
    }
    
    /**
     * Get all claim mappings for a provider
     */
    @Transactional(readOnly = true)
    public List<ClaimMapping> getMappingsByProvider(UUID providerId) {
        // Validate provider exists
        providerService.getProviderById(providerId);
        
        return claimMappingRepository.findByProviderId(providerId);
    }
    
    /**
     * Update claim mapping
     */
    public ClaimMapping updateMapping(UUID mappingId, ClaimMappingRequest request) {
        log.info("Updating claim mapping: {}", mappingId);
        
        ClaimMapping mapping = claimMappingRepository.findActiveById(mappingId)
                .orElseThrow(() -> new ResourceNotFoundException("ClaimMapping", mappingId));
        
        // Update fields if provided
        if (request.getName() != null) {
            mapping.setName(request.getName());
        }
        
        if (request.getDescription() != null) {
            mapping.setDescription(request.getDescription());
        }
        
        if (request.getSourcePath() != null) {
            validateJsonPath(request.getSourcePath());
            mapping.setSourcePath(request.getSourcePath());
        }
        
        if (request.getTargetField() != null) {
            mapping.setTargetField(request.getTargetField());
        }
        
        if (request.getTransform() != null) {
            mapping.setTransform(request.getTransform());
        }
        
        if (request.getPriority() != null) {
            mapping.setPriority(request.getPriority());
        }
        
        if (request.getIsActive() != null) {
            mapping.setIsActive(request.getIsActive());
        }
        
        mapping = claimMappingRepository.save(mapping);
        log.info("Claim mapping updated: {}", mappingId);
        
        return mapping;
    }
    
    /**
     * Delete claim mapping
     */
    public void deleteMapping(UUID id) {
        log.warn("Deleting claim mapping: {}", id);
        
        ClaimMapping mapping = claimMappingRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClaimMapping", id));
        
        mapping.softDelete();
        mapping.setIsActive(false);
        
        claimMappingRepository.save(mapping);
        log.info("Claim mapping deleted (soft): {}", id);
    }
    
    /**
     * Apply all active mappings for a provider to raw claims
     */
    @Transactional(readOnly = true)
    public Map<String, Object> applyMappings(UUID providerId, Map<String, Object> rawClaims) {
        if (rawClaims == null || rawClaims.isEmpty()) {
            return Map.of();
        }
        
        // Get active mappings for provider (ordered by priority DESC)
        List<ClaimMapping> mappings = claimMappingRepository.findActiveByProviderId(providerId);
        
        if (mappings.isEmpty()) {
            return new HashMap<>(rawClaims);
        }
        
        Map<String, Object> result = new HashMap<>(rawClaims);
        
        // Apply each mapping in priority order
        for (ClaimMapping mapping : mappings) {
            try {
                applyMapping(result, mapping);
            } catch (Exception e) {
                log.warn("Failed to apply claim mapping {} for provider {}: {}", 
                        mapping.getName(), providerId, e.getMessage());
                // Continue with other mappings
            }
        }
        
        return result;
    }
    
    /**
     * Apply a single mapping to claims
     */
    private void applyMapping(Map<String, Object> claims, ClaimMapping mapping) {
        try {
            // Extract value using JSONPath
            Object value = extractValue(claims, mapping.getSourcePath());
            
            if (value == null) {
                log.debug("No value found for path: {}", mapping.getSourcePath());
                return;
            }
            
            // Apply transformations
            Object transformedValue = applyTransformations(value, mapping.getTransform());
            
            // Set target field
            setNestedField(claims, mapping.getTargetField(), transformedValue);
            
        } catch (Exception e) {
            log.error("Error applying claim mapping {}: {}", mapping.getName(), e.getMessage());
            throw new ValidationException("Failed to apply claim mapping: " + mapping.getName(), e);
        }
    }
    
    /**
     * Extract value from claims using JSONPath
     */
    private Object extractValue(Map<String, Object> claims, String path) {
        try {
            // Simple path (no JSONPath syntax)
            if (!path.contains("$") && !path.contains("[") && !path.contains(".")) {
                return claims.get(path);
            }
            
            // JSONPath
            return JsonPath.read(claims, path);
            
        } catch (Exception e) {
            log.debug("Failed to extract value for path {}: {}", path, e.getMessage());
            return null;
        }
    }
    
    /**
     * Apply transformations to a value
     */
    private Object applyTransformations(Object value, Map<String, Object> transform) {
        if (transform == null || transform.isEmpty()) {
            return value;
        }
        
        Object result = value;
        
        // String transformations
        if (transform.containsKey("toLowerCase") && Boolean.TRUE.equals(transform.get("toLowerCase"))) {
            result = result.toString().toLowerCase();
        }
        
        if (transform.containsKey("toUpperCase") && Boolean.TRUE.equals(transform.get("toUpperCase"))) {
            result = result.toString().toUpperCase();
        }
        
        if (transform.containsKey("trim") && Boolean.TRUE.equals(transform.get("trim"))) {
            result = result.toString().trim();
        }
        
        // Regex replacement
        if (transform.containsKey("regex") && transform.containsKey("replacement")) {
            String regex = (String) transform.get("regex");
            String replacement = (String) transform.get("replacement");
            result = result.toString().replaceAll(regex, replacement);
        }
        
        // Default value if null/empty
        if (transform.containsKey("default") && (result == null || result.toString().isEmpty())) {
            result = transform.get("default");
        }
        
        return result;
    }
    
    /**
     * Set a nested field in claims map
     */
    private void setNestedField(Map<String, Object> claims, String field, Object value) {
        if (!field.contains(".")) {
            // Simple field
            claims.put(field, value);
            return;
        }
        
        // Nested field (e.g., "user.profile.name")
        String[] parts = field.split("\\.");
        Map<String, Object> current = claims;
        
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            
            if (!current.containsKey(part)) {
                current.put(part, new HashMap<String, Object>());
            }
            
            Object next = current.get(part);
            if (!(next instanceof Map)) {
                // Can't navigate further, overwrite
                Map<String, Object> newMap = new HashMap<>();
                current.put(part, newMap);
                current = newMap;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> nextMap = (Map<String, Object>) next;
                current = nextMap;
            }
        }
        
        current.put(parts[parts.length - 1], value);
    }
    
    /**
     * Validate JSONPath expression
     */
    private void validateJsonPath(String path) {
        if (path == null || path.isBlank()) {
            throw new ValidationException("Source path cannot be null or empty");
        }
        
        // Basic validation - try to compile the path
        if (path.startsWith("$")) {
            try {
                JsonPath.compile(path);
            } catch (Exception e) {
                throw new ValidationException("Invalid JSONPath expression: " + path, e);
            }
        }
    }
}
