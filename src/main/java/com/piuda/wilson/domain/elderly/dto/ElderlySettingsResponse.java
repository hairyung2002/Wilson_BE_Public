package com.piuda.wilson.domain.elderly.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import java.time.LocalTime;

public record ElderlySettingsResponse(
        boolean proactiveEnabled,
        @JsonFormat(pattern = "HH:mm") LocalTime quietHoursStart,
        @JsonFormat(pattern = "HH:mm") LocalTime quietHoursEnd
) {

    public static ElderlySettingsResponse from(Elderly elderly) {
        return new ElderlySettingsResponse(elderly.isProactiveEnabled(), elderly.getQuietHoursStart(),
                elderly.getQuietHoursEnd());
    }
}
