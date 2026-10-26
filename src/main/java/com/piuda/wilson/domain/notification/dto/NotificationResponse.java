package com.piuda.wilson.domain.notification.dto;

import com.piuda.wilson.domain.notification.entity.Notification;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(Long notificationId, NotificationType type, Long elderlyId, String title,
                                   String body, boolean read, LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(),
                notification.getElderly() == null ? null : notification.getElderly().getId(),
                notification.getTitle(), notification.getBody(), notification.isRead(), notification.getCreatedAt());
    }
}
