package com.piuda.wilson.domain.caregiver.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 보호자 초대 코드 저장소. 코드는 TTL로 만료되고, 노인당 유효한 코드는 하나뿐이다.
 *
 * <p>{@code invite-code:{code}} → 노인 ID, {@code invite-code:elderly:{elderlyId}} → 현재 코드
 * (새 코드를 발급하면 이전 코드를 지우기 위함).</p>
 */
@Repository
@RequiredArgsConstructor
public class InviteCodeRepository {

    private static final String CODE_PREFIX = "invite-code:";
    private static final String ELDERLY_PREFIX = "invite-code:elderly:";

    private final RedisTemplate<String, Object> redisTemplate;

    /** 코드가 다른 코드와 겹치면 false. 저장되면 그 노인의 이전 코드는 무효가 된다. */
    public boolean issue(String code, Long elderlyId, Duration ttl) {
        Boolean saved = redisTemplate.opsForValue().setIfAbsent(codeKey(code), String.valueOf(elderlyId), ttl);
        if (!Boolean.TRUE.equals(saved)) {
            return false;
        }
        String elderlyKey = elderlyKey(elderlyId);
        Object previous = redisTemplate.opsForValue().getAndSet(elderlyKey, code);
        redisTemplate.expire(elderlyKey, ttl);
        if (previous != null && !code.equals(previous.toString())) {
            redisTemplate.delete(codeKey(previous.toString()));
        }
        return true;
    }

    public Optional<Long> findElderlyId(String code) {
        Object value = redisTemplate.opsForValue().get(codeKey(code));
        return Optional.ofNullable(value).map(v -> Long.valueOf(v.toString()));
    }

    public void delete(String code, Long elderlyId) {
        redisTemplate.delete(codeKey(code));
        String elderlyKey = elderlyKey(elderlyId);
        Object current = redisTemplate.opsForValue().get(elderlyKey);
        if (current != null && code.equals(current.toString())) {
            redisTemplate.delete(elderlyKey);
        }
    }

    private static String codeKey(String code) {
        return CODE_PREFIX + code;
    }

    private static String elderlyKey(Long elderlyId) {
        return ELDERLY_PREFIX + elderlyId;
    }
}
