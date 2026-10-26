package com.piuda.wilson.domain.notification.dto;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.notification.entity.NotificationType;

/** 실제로 적용되는 값 (직접 정하지 않은 항목은 역할 기본값). */
public record NotificationSettingsResponse(boolean patternChange, boolean proactiveNoResponse, boolean dailySummary) {

    public static NotificationSettingsResponse from(CareRelationship careRelationship) {
        return new NotificationSettingsResponse(
                careRelationship.wantsNotification(NotificationType.PATTERN_CHANGE),
                careRelationship.wantsNotification(NotificationType.PROACTIVE_NO_RESPONSE),
                careRelationship.wantsNotification(NotificationType.DAILY_SUMMARY));
    }
}
