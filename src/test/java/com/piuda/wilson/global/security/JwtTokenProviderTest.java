package com.piuda.wilson.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.member.entity.MemberType;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "wilson-test-secret-key-must-be-at-least-256-bits-long-0123456789",
                3600000L, 1209600000L);
        jwtTokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    @DisplayName("액세스 토큰을 발급하고 검증하여 memberId를 복원한다")
    void createAndParseToken() {
        // given
        Long memberId = 42L;

        // when
        String token = jwtTokenProvider.createToken(memberId, MemberType.CAREGIVER);

        // then
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("리프레시 토큰을 발급하고 검증하여 memberId를 복원한다")
    void createAndParseRefreshToken() {
        // given
        Long memberId = 99L;

        // when
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId);

        // then
        assertThat(jwtTokenProvider.validateToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.getMemberId(refreshToken)).isEqualTo(99L);
    }

    @Test
    @DisplayName("토큰의 발급 시각을 밀리초 단위로 읽는다 (로그아웃 직후 재로그인 토큰과 구분하기 위함)")
    void getIssuedAtEpochMilli() {
        // given
        long before = Instant.now().toEpochMilli();

        // when
        String token = jwtTokenProvider.createToken(42L, MemberType.CAREGIVER);

        // then
        assertThat(jwtTokenProvider.getIssuedAtEpochMilli(token))
                .isBetween(before, Instant.now().toEpochMilli());
        assertThat(jwtTokenProvider.accessExpirationMillis()).isEqualTo(3600000L);
    }

    @Test
    @DisplayName("변조되거나 형식이 잘못된 토큰은 검증에 실패한다")
    void validateToken_invalid() {
        // when & then
        assertThat(jwtTokenProvider.validateToken("not-a-jwt-token")).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
    }
}
