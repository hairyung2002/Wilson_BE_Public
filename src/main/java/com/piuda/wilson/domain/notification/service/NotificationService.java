package com.piuda.wilson.domain.notification.service;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.notification.dto.NotificationResponse;
import com.piuda.wilson.domain.notification.dto.UnreadCountResponse;
import com.piuda.wilson.domain.notification.entity.Notification;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.entity.PushStatus;
import com.piuda.wilson.domain.notification.repository.NotificationRepository;
import com.piuda.wilson.global.common.response.PageResponse;
import com.piuda.wilson.global.common.time.QuietHours;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자 알림 (api-spec 14장, 결정 E1~E4).
 *
 * <p>응급은 연결된 보호자 전원에게, 나머지는 각자 설정(기본: OWNER만)을 따른다.
 * 앱 내 목록에는 바로 저장하고, 푸시는 저장이 커밋된 뒤 보낸다. 22:00~07:00에는 응급 외 푸시를 07:00로 미룬다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CareRelationshipRepository careRelationshipRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public void notifyCaregivers(Long elderlyId, NotificationType type, String title, String body) {
        List<CareRelationship> recipients = careRelationshipRepository.findAllWithCaregiverByElderlyId(elderlyId).stream()
                .filter(relationship -> relationship.wantsNotification(type))
                .toList();
        if (recipients.isEmpty()) {
            log.info("Notification has no recipients: elderlyId={}, type={}", elderlyId, type);
            return;
        }
        PushStatus pushStatus = pushNow(type) ? PushStatus.QUEUED : PushStatus.DEFERRED;
        List<Long> queuedIds = recipients.stream()
                .map(relationship -> notificationRepository.save(Notification.builder()
                        .recipient(relationship.getCaregiver().getMember())
                        .elderly(relationship.getElderly())
                        .type(type)
                        .title(title)
                        .body(body)
                        .pushStatus(pushStatus)
                        .build()))
                .filter(notification -> notification.getPushStatus() == PushStatus.QUEUED)
                .map(Notification::getId)
                .toList();
        if (!queuedIds.isEmpty()) {
            eventPublisher.publishEvent(new NotificationsQueuedEvent(queuedIds));
        }
        log.info("Notification created: elderlyId={}, type={}, recipients={}, push={}",
                elderlyId, type, recipients.size(), pushStatus);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(Long memberId, Pageable pageable) {
        return PageResponse.from(notificationRepository.findByRecipientIdOrderByIdDesc(memberId, pageable)
                .map(NotificationResponse::from));
    }

    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        notificationRepository.findByIdAndRecipientId(notificationId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND))
                .markAsRead();
    }

    @Transactional
    public void markAllRead(Long memberId) {
        notificationRepository.markAllRead(memberId);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse countUnread(Long memberId) {
        return new UnreadCountResponse(notificationRepository.countByRecipientIdAndIsReadFalse(memberId));
    }

    private boolean pushNow(NotificationType type) {
        return type == NotificationType.EMERGENCY || !QuietHours.DEFAULT.contains(LocalTime.now(clock));
    }
}
