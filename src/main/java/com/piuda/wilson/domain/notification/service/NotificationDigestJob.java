package com.piuda.wilson.domain.notification.service;

import com.piuda.wilson.global.scheduler.SchedulerLock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 밤사이(22:00~07:00) 미뤄 둔 보호자 알림을 07:00(KST)에 묶어 보낸다 (결정 E4). */
@Component
@RequiredArgsConstructor
public class NotificationDigestJob {

    private static final String JOB_NAME = "notification-digest";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final NotificationDispatcher notificationDispatcher;
    private final SchedulerLock schedulerLock;

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
    public void sendDeferred() {
        if (schedulerLock.tryLock(JOB_NAME, LOCK_TTL)) {
            notificationDispatcher.dispatchDeferred();
        }
    }
}
