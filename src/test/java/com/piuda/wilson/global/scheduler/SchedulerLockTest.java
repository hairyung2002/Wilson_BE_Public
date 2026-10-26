package com.piuda.wilson.global.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SchedulerLockTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SchedulerLock schedulerLock;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("작업별 키를 먼저 잡은 Pod만 실행한다 (키는 실행 간격만큼 유지)")
    void tryLock() {
        // given
        given(valueOperations.setIfAbsent(eq("scheduler-lock:orphan-cleanup"), anyString(), eq(Duration.ofMinutes(30))))
                .willReturn(true, false);

        // when & then
        assertThat(schedulerLock.tryLock("orphan-cleanup", Duration.ofMinutes(30))).isTrue();
        assertThat(schedulerLock.tryLock("orphan-cleanup", Duration.ofMinutes(30))).isFalse();
    }

    @Test
    @DisplayName("Redis 장애면 중복 실행을 막을 수 없으므로 실행하지 않는다")
    void tryLock_redisDown() {
        // given
        given(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(30))))
                .willThrow(new RedisConnectionFailureException("down"));

        // when & then
        assertThat(schedulerLock.tryLock("orphan-cleanup", Duration.ofMinutes(30))).isFalse();
    }
}
