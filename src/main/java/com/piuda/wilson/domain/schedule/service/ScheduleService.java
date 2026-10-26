package com.piuda.wilson.domain.schedule.service;

import com.piuda.wilson.domain.elderly.service.ElderlyAccess;
import com.piuda.wilson.domain.elderly.service.ElderlyAccessService;
import com.piuda.wilson.domain.schedule.dto.CreateScheduleRequest;
import com.piuda.wilson.domain.schedule.dto.CreateScheduleResponse;
import com.piuda.wilson.domain.schedule.dto.ScheduleResponse;
import com.piuda.wilson.domain.schedule.dto.UpdateScheduleRequest;
import com.piuda.wilson.domain.schedule.entity.Schedule;
import com.piuda.wilson.domain.schedule.entity.ScheduleAuthor;
import com.piuda.wilson.domain.schedule.entity.Weekday;
import com.piuda.wilson.domain.schedule.repository.ScheduleRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 노인별 반복 일정 (api-spec 11장). 연결된 보호자(OWNER·MEMBER)와 노인 본인 모두 관리한다 (결정 C2). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ElderlyAccessService elderlyAccessService;

    @Transactional
    public CreateScheduleResponse create(Long memberId, Long elderlyId, CreateScheduleRequest request) {
        ElderlyAccess access = elderlyAccessService.requireViewer(memberId, elderlyId);
        Set<DayOfWeek> days = toDays(request.daysOfWeek());
        LocalTime time = toMinute(request.time());
        validateNoOverlap(elderlyId, null, time, days);

        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .elderly(access.elderly())
                .type(request.type())
                .label(request.label())
                .time(time)
                .daysOfWeek(days)
                .enabled(request.enabled() == null || request.enabled())
                .createdBy(access.isSelf() ? ScheduleAuthor.ELDERLY : ScheduleAuthor.CAREGIVER)
                .build());
        log.info("Schedule created: elderlyId={}, scheduleId={}, by={}", elderlyId, schedule.getId(), access.actor());
        return new CreateScheduleResponse(schedule.getId());
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedules(Long memberId, Long elderlyId) {
        elderlyAccessService.requireViewer(memberId, elderlyId);
        return scheduleRepository.findByElderlyIdOrderByTimeAsc(elderlyId).stream().map(ScheduleResponse::from).toList();
    }

    @Transactional
    public ScheduleResponse update(Long memberId, Long scheduleId, UpdateScheduleRequest request) {
        Schedule schedule = findAccessible(memberId, scheduleId);
        Set<DayOfWeek> days = request.daysOfWeek() == null ? null : toDays(request.daysOfWeek());
        LocalTime time = request.time() == null ? null : toMinute(request.time());
        if (time != null || days != null) {
            validateNoOverlap(schedule.getElderly().getId(), scheduleId,
                    time != null ? time : schedule.getTime(), days != null ? days : schedule.getDaysOfWeek());
        }
        schedule.update(request.type(), request.label(), time, days, request.enabled());
        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public void delete(Long memberId, Long scheduleId) {
        scheduleRepository.delete(findAccessible(memberId, scheduleId));
        log.info("Schedule deleted: scheduleId={}", scheduleId);
    }

    private Schedule findAccessible(Long memberId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
        elderlyAccessService.requireViewer(memberId, schedule.getElderly().getId());
        return schedule;
    }

    /** 같은 노인에게 같은 시각·겹치는 요일의 일정이 있으면 같은 시각에 선제 대화가 두 번 걸린다. */
    private void validateNoOverlap(Long elderlyId, Long excludeScheduleId, LocalTime time, Set<DayOfWeek> days) {
        boolean overlaps = scheduleRepository.findByElderlyIdOrderByTimeAsc(elderlyId).stream()
                .filter(other -> !other.getId().equals(excludeScheduleId))
                .anyMatch(other -> other.overlaps(time, days));
        if (overlaps) {
            throw new CustomException(ErrorCode.DUPLICATE_SCHEDULE_TIME);
        }
    }

    private static Set<DayOfWeek> toDays(List<Weekday> weekdays) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        weekdays.forEach(weekday -> days.add(weekday.toDayOfWeek()));
        if (days.size() != weekdays.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return days;
    }

    /** 스케줄러는 분 단위로 돌므로 초 이하는 버린다. */
    private static LocalTime toMinute(LocalTime time) {
        return time.withSecond(0).withNano(0);
    }
}
