package com.piuda.wilson.domain.schedule.dto;

import com.piuda.wilson.domain.schedule.entity.ScheduleType;
import com.piuda.wilson.domain.schedule.entity.Weekday;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;

/** enabled를 생략하면 켜진 상태로 등록한다. time은 KST "HH:mm". */
public record CreateScheduleRequest(

        @NotNull(message = "일정 종류는 필수입니다.")
        ScheduleType type,

        @Size(max = 30, message = "일정 이름은 30자 이하여야 합니다.")
        String label,

        @NotNull(message = "시각은 필수입니다.")
        LocalTime time,

        @NotEmpty(message = "요일은 하나 이상이어야 합니다.")
        @Size(max = 7, message = "요일은 7개 이하여야 합니다.")
        List<Weekday> daysOfWeek,

        Boolean enabled
) {
}
