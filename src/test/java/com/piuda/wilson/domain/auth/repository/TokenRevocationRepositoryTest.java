package com.piuda.wilson.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenRevocationRepositoryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private TokenRevocationRepository tokenRevocationRepository;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("회원의 토큰 무효화 시각을 액세스 토큰 수명만큼 보관한다")
    void revokeAllIssuedBefore() {
        // when
        tokenRevocationRepository.revokeAllIssuedBefore(5L, 1_700_000_000_123L, Duration.ofHours(1));

        // then
        verify(valueOperations).set("token-revoked:5", "1700000000123", Duration.ofHours(1));
    }

    @Test
    @DisplayName("무효화 시각을 조회한다")
    void findRevokedAt() {
        // given
        given(valueOperations.get("token-revoked:5")).willReturn("1700000000123");

        // when & then
        assertThat(tokenRevocationRepository.findRevokedAtEpochMilli(5L)).contains(1_700_000_000_123L);
    }

    @Test
    @DisplayName("무효화 기록이 없으면 빈 값을 반환한다")
    void findRevokedAt_none() {
        // given
        given(valueOperations.get("token-revoked:5")).willReturn(null);

        // when & then
        assertThat(tokenRevocationRepository.findRevokedAtEpochMilli(5L)).isEmpty();
    }
}
