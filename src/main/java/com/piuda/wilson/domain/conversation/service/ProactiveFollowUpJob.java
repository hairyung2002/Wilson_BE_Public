package com.piuda.wilson.domain.conversation.service;

import com.piuda.wilson.global.scheduler.SchedulerLock;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매분 선제 대화 무응답을 확인한다: 10분 뒤 재푸시, 다시 10분 뒤 보호자에게 알림 (결정 C5). */
@Component
@RequiredArgsConstructor
public class ProactiveFollowUpJob {

    private static final String JOB_NAME = "proactive-follow-up:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);

    private final ProactiveService proactiveService;
    private final SchedulerLock schedulerLock;
    private final Clock clock;

    @Scheduled(cron = "30 * * * * *", zone = "Asia/Seoul")
    public void followUp() {
        LocalDateTime minute = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        if (schedulerLock.tryLock(JOB_NAME + minute, LOCK_TTL)) {
            proactiveService.followUp();
        }
    }
}
