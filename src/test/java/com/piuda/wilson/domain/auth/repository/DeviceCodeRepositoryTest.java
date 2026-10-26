package com.piuda.wilson.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
class DeviceCodeRepositoryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private DeviceCodeRepository deviceCodeRepository;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("코드가 비어 있으면 노인 식별자를 TTL과 함께 저장한다")
    void saveIfAbsent_success() {
        // given
        Duration ttl = Duration.ofMinutes(10);
        given(valueOperations.setIfAbsent("device-code:123456", "15", ttl)).willReturn(true);

        // when
        boolean saved = deviceCodeRepository.saveIfAbsent("123456", 15L, ttl);

        // then
        assertThat(saved).isTrue();
    }

    @Test
    @DisplayName("이미 사용 중인 코드면 저장하지 않는다")
    void saveIfAbsent_duplicate() {
        // given
        Duration ttl = Duration.ofMinutes(10);
        given(valueOperations.setIfAbsent("device-code:123456", "15", ttl)).willReturn(false);

        // when
        boolean saved = deviceCodeRepository.saveIfAbsent("123456", 15L, ttl);

        // then
        assertThat(saved).isFalse();
    }

    @Test
    @DisplayName("코드를 소비하면 노인 식별자를 반환하고 즉시 삭제한다")
    void consume_success() {
        // given
        given(valueOperations.getAndDelete("device-code:123456")).willReturn("15");

        // when & then
        assertThat(deviceCodeRepository.consume("123456")).contains(15L);
    }

    @Test
    @DisplayName("없거나 만료된 코드는 빈 값을 반환한다")
    void consume_missing() {
        // given
        given(valueOperations.getAndDelete("device-code:000000")).willReturn(null);

        // when & then
        assertThat(deviceCodeRepository.consume("000000")).isEmpty();
    }
}
