package com.piuda.wilson.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.piuda.wilson.domain.schedule.entity.Schedule;
import com.piuda.wilson.domain.schedule.entity.ScheduleAuthor;
import com.piuda.wilson.domain.schedule.entity.ScheduleType;
import com.piuda.wilson.domain.schedule.entity.Weekday;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record ScheduleResponse(Long scheduleId, ScheduleType type, String label,
                               @JsonFormat(pattern = "HH:mm") LocalTime time, List<Weekday> daysOfWeek,
                               boolean enabled, ScheduleAuthor createdBy, LocalDateTime updatedAt) {

    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(schedule.getId(), schedule.getType(), schedule.getLabel(), schedule.getTime(),
                schedule.getDaysOfWeek().stream().sorted().map(Weekday::from).toList(), schedule.isEnabled(),
                schedule.getCreatedBy(), schedule.getUpdatedAt());
    }
}
