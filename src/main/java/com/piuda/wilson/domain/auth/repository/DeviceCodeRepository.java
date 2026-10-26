package com.piuda.wilson.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/** 기기 연결용 일회성 코드 저장소. 코드는 TTL로 만료되고, 한 번 소비하면 삭제된다. */
@Repository
@RequiredArgsConstructor
public class DeviceCodeRepository {

    private static final String KEY_PREFIX = "device-code:";

    private final RedisTemplate<String, Object> redisTemplate;

    public boolean saveIfAbsent(String code, Long elderlyId, Duration ttl) {
        Boolean saved = redisTemplate.opsForValue().setIfAbsent(key(code), String.valueOf(elderlyId), ttl);
        return Boolean.TRUE.equals(saved);
    }

    public Optional<Long> consume(String code) {
        Object value = redisTemplate.opsForValue().getAndDelete(key(code));
        return Optional.ofNullable(value).map(v -> Long.valueOf(v.toString()));
    }

    private String key(String code) {
        return KEY_PREFIX + code;
    }
}
