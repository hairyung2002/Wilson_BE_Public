package com.piuda.wilson.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 저장이 커밋된 뒤에 푸시한다. 롤백된 알림이 기기로 나가지 않게 하기 위함이다.
 * 트랜잭션 밖에서 발행되면(fallbackExecution) 바로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    private final NotificationDispatcher notificationDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQueued(NotificationsQueuedEvent event) {
        notificationDispatcher.dispatch(event.notificationIds());
    }
}
