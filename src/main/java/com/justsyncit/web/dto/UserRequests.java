package com.justsyncit.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTOs for User Collection API requests.
 */
public class UserRequests {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginRequest(String username, String password) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateUserRequest(String username, String password, String displayName, String role) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateUserRequest(String password, String displayName, String role) {
    }

    // Response DTO to avoid exposing password hash/salt
    public record UserResponse(String id, String username, String displayName, String role) {
    }
}
