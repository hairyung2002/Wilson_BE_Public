package com.piuda.wilson.domain.elderly.dto;

import java.time.LocalTime;

/** 보낸 항목만 바꾼다. 시각은 KST "HH:mm". */
public record ElderlySettingsRequest(Boolean proactiveEnabled, LocalTime quietHoursStart, LocalTime quietHoursEnd) {
}
