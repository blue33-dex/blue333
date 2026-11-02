package com.blue333.ordermgmt.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    AuthenticatedUserResponse user) {
}
