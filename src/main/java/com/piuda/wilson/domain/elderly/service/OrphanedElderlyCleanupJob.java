package com.piuda.wilson.domain.elderly.service;

import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.global.scheduler.SchedulerLock;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보호자 없이 30일이 지난 노인을 매일 04:00(KST)에 파기한다 (api-spec 4-4, 5-5).
 * 그 사이 노인 본인이 초대 코드로 새 보호자를 연결하면 대상에서 빠진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanedElderlyCleanupJob {

    private static final String JOB_NAME = "orphaned-elderly-cleanup";
    private static final Duration GRACE_PERIOD = Duration.ofDays(30);
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final ElderlyRepository elderlyRepository;
    private final ElderlyDeletionService elderlyDeletionService;
    private final SchedulerLock schedulerLock;
    private final Clock clock;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void cleanUp() {
        if (!schedulerLock.tryLock(JOB_NAME, LOCK_TTL)) {
            return;
        }
        List<Long> elderlyIds = elderlyRepository.findOrphanedIdsBefore(LocalDateTime.now(clock).minus(GRACE_PERIOD));
        for (Long elderlyId : elderlyIds) {
            try {
                elderlyDeletionService.erase(elderlyId);
            } catch (RuntimeException e) {
                log.error("Orphaned elderly cleanup failed, will retry tomorrow: elderlyId={}", elderlyId, e);
            }
        }
        log.info("Orphaned elderly cleanup finished: targets={}", elderlyIds.size());
    }
}
