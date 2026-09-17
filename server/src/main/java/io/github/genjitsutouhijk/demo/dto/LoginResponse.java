package io.github.genjitsutouhijk.demo.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String username
) {}