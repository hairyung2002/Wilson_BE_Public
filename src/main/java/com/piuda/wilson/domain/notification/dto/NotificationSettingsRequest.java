package com.piuda.wilson.domain.notification.dto;

/** 보낸 항목만 바꾼다. 응급 알림은 설정 항목이 없다 (항상 받음). */
public record NotificationSettingsRequest(Boolean patternChange, Boolean proactiveNoResponse, Boolean dailySummary) {
}
