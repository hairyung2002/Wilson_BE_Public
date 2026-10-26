package com.piuda.wilson.domain.auth.repository;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/** 인증 시도 횟수 집계. 집계 기간은 첫 시도 시점부터 고정된다. */
@Repository
@RequiredArgsConstructor
public class LoginAttemptRepository {

    private static final String DEVICE_LOGIN_PREFIX = "rate:device-login:";
    private static final String INVITE_CODE_PREFIX = "rate:invite-code:";

    private final RedisTemplate<String, Object> redisTemplate;

    public long incrementDeviceLogin(String clientIp, Duration window) {
        return increment(DEVICE_LOGIN_PREFIX + clientIp, window);
    }

    /** 초대 코드는 로그인한 보호자가 입력하므로 회원 단위로 센다. */
    public long incrementInviteCode(Long memberId, Duration window) {
        return increment(INVITE_CODE_PREFIX + memberId, window);
    }

    private long increment(String key, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count == null ? 0L : count;
    }
}
