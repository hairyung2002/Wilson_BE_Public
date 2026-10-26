package com.piuda.wilson.domain.schedule.dto;

import com.piuda.wilson.domain.schedule.entity.ScheduleType;
import com.piuda.wilson.domain.schedule.entity.Weekday;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;

/** 보낸 항목만 고친다. */
public record UpdateScheduleRequest(

        ScheduleType type,

        @Size(max = 30, message = "일정 이름은 30자 이하여야 합니다.")
        String label,

        LocalTime time,

        @Size(min = 1, max = 7, message = "요일은 1~7개여야 합니다.")
        List<Weekday> daysOfWeek,

        Boolean enabled
) {
}
