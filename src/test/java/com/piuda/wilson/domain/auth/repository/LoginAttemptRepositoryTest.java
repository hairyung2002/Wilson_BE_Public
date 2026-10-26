package com.piuda.wilson.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
class LoginAttemptRepositoryTest {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private LoginAttemptRepository loginAttemptRepository;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("첫 시도면 횟수를 1로 만들고 집계 기간만큼 만료 시간을 건다")
    void increment_first() {
        // given
        given(valueOperations.increment("rate:device-login:1.2.3.4")).willReturn(1L);

        // when
        long count = loginAttemptRepository.incrementDeviceLogin("1.2.3.4", WINDOW);

        // then
        assertThat(count).isEqualTo(1L);
        verify(redisTemplate).expire("rate:device-login:1.2.3.4", WINDOW);
    }

    @Test
    @DisplayName("이미 집계 중이면 횟수만 늘리고 만료 시간은 연장하지 않는다")
    void increment_subsequent() {
        // given
        given(valueOperations.increment("rate:device-login:1.2.3.4")).willReturn(4L);

        // when
        long count = loginAttemptRepository.incrementDeviceLogin("1.2.3.4", WINDOW);

        // then
        assertThat(count).isEqualTo(4L);
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("초대 코드 시도 횟수는 회원 단위로 따로 센다")
    void incrementInviteCode() {
        // given
        given(valueOperations.increment("rate:invite-code:7")).willReturn(1L);

        // when
        long count = loginAttemptRepository.incrementInviteCode(7L, WINDOW);

        // then
        assertThat(count).isEqualTo(1L);
        verify(redisTemplate).expire("rate:invite-code:7", WINDOW);
    }
}
