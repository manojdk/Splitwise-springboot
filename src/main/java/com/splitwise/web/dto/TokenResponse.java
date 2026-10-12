package com.splitwise.web.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
