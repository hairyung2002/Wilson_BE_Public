package com.piuda.wilson.domain.notification.service;

import java.util.List;

/** 방금 저장한 알림 중 지금 푸시할 것들. 저장이 커밋된 뒤에 보낸다. */
public record NotificationsQueuedEvent(List<Long> notificationIds) {
}
