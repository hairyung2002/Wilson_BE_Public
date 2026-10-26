package com.piuda.wilson.global.scheduler;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * BE Pod가 여러 개여도 예약 작업을 한 Pod만 실행하게 하는 Redis 락 (api-spec 17장).
 *
 * <p>락은 풀지 않고 TTL로 만료시킨다. TTL은 작업 시간보다 길고 실행 간격보다 짧게 잡아,
 * 같은 회차를 다른 Pod가 다시 실행하지 않게 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerLock {

    private static final String KEY_PREFIX = "scheduler-lock:";
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    private final StringRedisTemplate redisTemplate;

    /** Redis 장애면 다른 Pod와 겹칠 수 있으므로 실행하지 않는다 (다음 회차에 다시 시도). */
    public boolean tryLock(String jobName, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + jobName, INSTANCE_ID, ttl));
        } catch (DataAccessException e) {
            log.warn("Scheduler lock unavailable, skipping job: job={}", jobName, e);
            return false;
        }
    }
}
