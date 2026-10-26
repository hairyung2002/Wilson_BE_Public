package com.piuda.wilson.domain.schedule.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.conversation.service.ProactiveService;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.schedule.entity.Schedule;
import com.piuda.wilson.domain.schedule.entity.ScheduleAuthor;
import com.piuda.wilson.domain.schedule.entity.ScheduleType;
import com.piuda.wilson.domain.schedule.repository.ScheduleRepository;
import com.piuda.wilson.global.common.time.QuietHours;
import com.piuda.wilson.global.scheduler.SchedulerLock;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProactiveDispatchJobTest {

    /** 2026-10-20(화) 08:30:12 KST */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T23:30:12Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime MINUTE = LocalDateTime.of(2026, 10, 20, 8, 30);

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ProactiveService proactiveService;

    @Mock
    private SchedulerLock schedulerLock;

    private ProactiveDispatchJob job;

    @BeforeEach
    void setUp() {
        job = new ProactiveDispatchJob(scheduleRepository, proactiveService, schedulerLock, CLOCK);
    }

    private static Schedule schedule(Elderly elderly, DayOfWeek day) {
        return Schedule.builder().elderly(elderly).type(ScheduleType.MEAL).time(LocalTime.of(8, 30))
                .daysOfWeek(EnumSet.of(day)).enabled(true).createdBy(ScheduleAuthor.CAREGIVER).build();
    }

    @Test
    @DisplayName("이번 분에 해당 요일 일정만, 선제 대화가 켜져 있고 방해 금지 시간이 아닌 노인에게 건다")
    void dispatch() {
        // given
        Elderly normal = Elderly.builder().name("보통").build();
        Elderly off = Elderly.builder().name("끔").build();
        off.updateSettings(false, QuietHours.DEFAULT);
        Elderly lateRiser = Elderly.builder().name("늦잠").build();
        lateRiser.updateSettings(null, new QuietHours(LocalTime.of(22, 0), LocalTime.of(9, 0)));
        Schedule due = schedule(normal, DayOfWeek.TUESDAY);
        given(schedulerLock.tryLock(eq("proactive-dispatch:2026-10-20T08:30"), any())).willReturn(true);
        given(scheduleRepository.findDueAt(LocalTime.of(8, 30))).willReturn(List.of(
                due, schedule(normal, DayOfWeek.WEDNESDAY), schedule(off, DayOfWeek.TUESDAY),
                schedule(lateRiser, DayOfWeek.TUESDAY)));

        // when
        job.dispatch();

        // then
        verify(proactiveService).trigger(due, MINUTE);
        verify(proactiveService).trigger(any(), any());
    }

    @Test
    @DisplayName("같은 분에 다른 Pod가 이미 실행했으면 아무것도 하지 않는다")
    void dispatch_locked() {
        // given
        given(schedulerLock.tryLock(eq("proactive-dispatch:2026-10-20T08:30"), any())).willReturn(false);

        // when
        job.dispatch();

        // then
        verify(scheduleRepository, never()).findDueAt(any());
    }
}
