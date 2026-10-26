package com.piuda.wilson.domain.notification.service;

import com.piuda.wilson.domain.notification.entity.Notification;
import com.piuda.wilson.domain.notification.entity.PushStatus;
import com.piuda.wilson.domain.notification.entity.PushToken;
import com.piuda.wilson.domain.notification.repository.NotificationRepository;
import com.piuda.wilson.domain.notification.repository.PushTokenRepository;
import com.piuda.wilson.infrastructure.push.PushMessage;
import com.piuda.wilson.infrastructure.push.PushResult;
import com.piuda.wilson.infrastructure.push.PushSender;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 알림을 보호자 기기로 푸시한다 (api-spec 14-2).
 *
 * <p>일시 오류는 3회까지 다시 보내고, FCM이 등록되지 않은 토큰이라고 하면 토큰을 지운다.
 * 호출한 쪽 트랜잭션이 이미 끝난 뒤(커밋 이후·예약 작업)에 불리므로 새 트랜잭션에서 상태를 기록한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private static final int MAX_ATTEMPTS = 3;
    private static final String DIGEST_TITLE = "윌슨";

    private final NotificationRepository notificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final PushSender pushSender;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(List<Long> notificationIds) {
        notificationRepository.findAllById(notificationIds).forEach(notification -> notification.recordPush(
                push(notification.getRecipient().getId(), notification.getTitle(), notification.getBody(),
                        data(notification)), LocalDateTime.now(clock)));
    }

    /** 밤사이 미뤄 둔 알림을 보호자·노인별로 묶어 한 번씩 보낸다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchDeferred() {
        Map<DeferredGroupKey, List<Notification>> groups = notificationRepository
                .findByPushStatusOrderByIdAsc(PushStatus.DEFERRED).stream()
                .collect(Collectors.groupingBy(DeferredGroupKey::of, LinkedHashMap::new, Collectors.toList()));
        groups.values().forEach(this::dispatchGroup);
        log.info("Deferred notifications dispatched: groups={}", groups.size());
    }

    private void dispatchGroup(List<Notification> group) {
        Notification first = group.get(0);
        boolean single = group.size() == 1;
        String title = single ? first.getTitle() : DIGEST_TITLE;
        String body = single ? first.getBody() : digestBody(first, group.size());
        PushStatus status = push(first.getRecipient().getId(), title, body, data(first));
        LocalDateTime now = LocalDateTime.now(clock);
        group.forEach(notification -> notification.recordPush(status, now));
    }

    private PushStatus push(Long memberId, String title, String body, Map<String, String> data) {
        Optional<PushToken> pushToken = pushTokenRepository.findByMemberId(memberId);
        if (pushToken.isEmpty()) {
            return PushStatus.NO_DEVICE;
        }
        PushMessage message = new PushMessage(pushToken.get().getToken(), title, body, data);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            PushResult result = pushSender.send(message);
            if (result == PushResult.SENT) {
                return PushStatus.SENT;
            }
            if (result == PushResult.INVALID_TOKEN) {
                pushTokenRepository.delete(pushToken.get());
                log.info("Invalid push token removed: memberId={}", memberId);
                return PushStatus.NO_DEVICE;
            }
        }
        log.warn("Push failed after {} attempts: memberId={}", MAX_ATTEMPTS, memberId);
        return PushStatus.FAILED;
    }

    private static Map<String, String> data(Notification notification) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", notification.getType().name());
        data.put("notificationId", String.valueOf(notification.getId()));
        if (notification.getElderly() != null) {
            data.put("elderlyId", String.valueOf(notification.getElderly().getId()));
        }
        return data;
    }

    private static String digestBody(Notification first, int count) {
        String name = first.getElderly() == null ? "" : first.getElderly().getName() + "님 관련 ";
        return name + "새 알림 " + count + "건이 있어요";
    }

    private record DeferredGroupKey(Long recipientId, Long elderlyId) {

        static DeferredGroupKey of(Notification notification) {
            return new DeferredGroupKey(notification.getRecipient().getId(),
                    notification.getElderly() == null ? null : notification.getElderly().getId());
        }
    }
}
