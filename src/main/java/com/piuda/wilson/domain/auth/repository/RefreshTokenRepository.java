package com.piuda.wilson.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final RedisTemplate<String, Object> redisTemplate;

    public void save(Long memberId, String refreshToken, long ttlMillis) {
        redisTemplate.opsForValue().set(key(memberId), refreshToken, Duration.ofMillis(ttlMillis));
    }

    public Optional<String> findByMemberId(Long memberId) {
        Object value = redisTemplate.opsForValue().get(key(memberId));
        return Optional.ofNullable(value).map(Object::toString);
    }

    public void deleteByMemberId(Long memberId) {
        redisTemplate.delete(key(memberId));
    }

    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }
}
