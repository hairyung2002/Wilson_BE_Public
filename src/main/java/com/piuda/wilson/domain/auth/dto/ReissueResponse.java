package com.piuda.wilson.domain.auth.dto;

public record ReissueResponse(String accessToken, String refreshToken, String tokenType) {

    private static final String BEARER = "Bearer";

    public static ReissueResponse of(String accessToken, String refreshToken) {
        return new ReissueResponse(accessToken, refreshToken, BEARER);
    }
}
