package com.piuda.wilson.domain.schedule.service;

import com.piuda.wilson.domain.conversation.service.ProactiveService;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.schedule.entity.Schedule;
import com.piuda.wilson.domain.schedule.repository.ScheduleRepository;
import com.piuda.wilson.global.scheduler.SchedulerLock;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매분 실행 시각이 된 일정으로 선제 대화를 건다 (api-spec 6-2 흐름 1·2).
 * 선제 대화가 꺼져 있거나 노인의 방해 금지 시간이면 건너뛴다. 분 단위 락으로 여러 Pod가 같은 일정을 두 번 걸지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProactiveDispatchJob {

    private static final String JOB_NAME = "proactive-dispatch:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);

    private final ScheduleRepository scheduleRepository;
    private final ProactiveService proactiveService;
    private final SchedulerLock schedulerLock;
    private final Clock clock;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void dispatch() {
        LocalDateTime minute = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        if (!schedulerLock.tryLock(JOB_NAME + minute, LOCK_TTL)) {
            return;
        }
        scheduleRepository.findDueAt(minute.toLocalTime()).stream()
                .filter(schedule -> schedule.runsOn(minute.getDayOfWeek()))
                .filter(schedule -> canTalkNow(schedule.getElderly(), minute))
                .forEach(schedule -> trigger(schedule, minute));
    }

    private static boolean canTalkNow(Elderly elderly, LocalDateTime minute) {
        return elderly.isProactiveEnabled() && !elderly.quietHours().contains(minute.toLocalTime());
    }

    private void trigger(Schedule schedule, LocalDateTime minute) {
        try {
            proactiveService.trigger(schedule, minute);
        } catch (RuntimeException e) {
            log.error("Proactive trigger failed: scheduleId={}", schedule.getId(), e);
        }
    }
}
