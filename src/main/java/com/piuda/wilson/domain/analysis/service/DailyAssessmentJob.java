package com.piuda.wilson.domain.analysis.service;

import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.global.scheduler.SchedulerLock;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 03:00(KST) 전날의 대화 지표로 노인마다 변화를 판정한다 (api-spec 12-3).
 * 노인 한 명이 실패해도 나머지는 계속하며, 한 명씩 따로 트랜잭션을 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyAssessmentJob {

    private static final String JOB_NAME = "daily-assessment";
    private static final Duration LOCK_TTL = Duration.ofHours(1);

    private final ElderlyRepository elderlyRepository;
    private final AssessmentService assessmentService;
    private final SchedulerLock schedulerLock;
    private final Clock clock;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void assess() {
        if (!schedulerLock.tryLock(JOB_NAME, LOCK_TTL)) {
            return;
        }
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        for (Long elderlyId : elderlyRepository.findActiveIds()) {
            try {
                assessmentService.assess(elderlyId, yesterday);
            } catch (RuntimeException e) {
                log.error("Daily assessment failed: elderlyId={}, date={}", elderlyId, yesterday, e);
            }
        }
    }
}
