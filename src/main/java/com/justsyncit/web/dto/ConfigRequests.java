package com.justsyncit.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTOs for Configuration API requests.
 */
public class ConfigRequests {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateConfigRequest(
            Integer webPort,
            Long defaultChunkSize,
            Boolean compressionEnabled,
            Integer compressionLevel,
            Boolean encryptionEnabled,
            Integer maxConcurrentBackups,
            Integer retentionDays) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddBackupSourceRequest(String path) {
    }
}
