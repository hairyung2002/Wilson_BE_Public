package com.piuda.wilson.domain.auth.dto;

import com.piuda.wilson.domain.member.entity.MemberType;

public record LoginResponse(String accessToken, String refreshToken, String tokenType,
                            Long memberId, MemberType memberType) {

    private static final String BEARER = "Bearer";

    public static LoginResponse of(String accessToken, String refreshToken, Long memberId, MemberType memberType) {
        return new LoginResponse(accessToken, refreshToken, BEARER, memberId, memberType);
    }
}
