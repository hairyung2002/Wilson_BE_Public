package com.piuda.wilson.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회원 단위 토큰 무효화 기록. 기록된 시각보다 먼저 발급된 액세스 토큰은 모두 무효다 (로그아웃·탈퇴).
 *
 * <p>액세스 토큰 수명이 지나면 기록도 필요 없으므로 같은 TTL로 보관한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class TokenRevocationRepository {

    private static final String KEY_PREFIX = "token-revoked:";

    private final RedisTemplate<String, Object> redisTemplate;

    public void revokeAllIssuedBefore(Long memberId, long revokedAtEpochMilli, Duration ttl) {
        redisTemplate.opsForValue().set(key(memberId), String.valueOf(revokedAtEpochMilli), ttl);
    }

    public Optional<Long> findRevokedAtEpochMilli(Long memberId) {
        Object value = redisTemplate.opsForValue().get(key(memberId));
        return Optional.ofNullable(value).map(v -> Long.valueOf(v.toString()));
    }

    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }
}
