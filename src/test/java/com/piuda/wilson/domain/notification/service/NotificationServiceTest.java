package com.piuda.wilson.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.notification.entity.Notification;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.entity.PushStatus;
import com.piuda.wilson.domain.notification.repository.NotificationRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long ELDERLY_ID = 15L;
    private static final Clock DAYTIME = Clock.fixed(Instant.parse("2026-10-20T05:00:00Z"), KST);   // 14:00 KST
    private static final Clock NIGHT = Clock.fixed(Instant.parse("2026-10-20T14:30:00Z"), KST);     // 23:30 KST

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CareRelationshipRepository careRelationshipRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Elderly elderly = withId(Elderly.builder().name("김철수").build(), ELDERLY_ID);
    private final AtomicLong ids = new AtomicLong(100);

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private NotificationService service(Clock clock) {
        return new NotificationService(notificationRepository, careRelationshipRepository, eventPublisher, clock);
    }

    private CareRelationship relationship(Long memberId, CareRole role) {
        Member member = withId(Member.builder().name("보호자" + memberId).memberType(MemberType.CAREGIVER).build(),
                memberId);
        Caregiver caregiver = withId(Caregiver.builder().member(member).build(), memberId + 10);
        return CareRelationship.builder().caregiver(caregiver).elderly(elderly).role(role).build();
    }

    private void givenCaregivers(CareRelationship... relationships) {
        given(careRelationshipRepository.findAllWithCaregiverByElderlyId(ELDERLY_ID)).willReturn(List.of(relationships));
        given(notificationRepository.save(any(Notification.class))).willAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", ids.getAndIncrement());
            return notification;
        });
    }

    private List<Notification> savedNotifications(int times) {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(times)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("응급은 연결된 보호자 전원에게 보내고, 커밋 뒤 즉시 푸시하도록 이벤트를 낸다")
    void emergency_toAll() {
        // given
        givenCaregivers(relationship(1L, CareRole.OWNER), relationship(2L, CareRole.MEMBER));

        // when
        service(NIGHT).notifyCaregivers(ELDERLY_ID, NotificationType.EMERGENCY, "응급", "바로 연락해 보세요");

        // then
        List<Notification> saved = savedNotifications(2);
        assertThat(saved).extracting(n -> n.getRecipient().getId()).containsExactly(1L, 2L);
        assertThat(saved).allSatisfy(n -> assertThat(n.getPushStatus()).isEqualTo(PushStatus.QUEUED));
        verify(eventPublisher).publishEvent(new NotificationsQueuedEvent(List.of(100L, 101L)));
    }

    @Test
    @DisplayName("응급이 아니면 기본적으로 주보호자에게만 보내고, MEMBER는 설정에서 켠 경우에만 받는다")
    void nonEmergency_byPreference() {
        // given
        CareRelationship member = relationship(2L, CareRole.MEMBER);
        CareRelationship optedInMember = relationship(3L, CareRole.MEMBER);
        optedInMember.updateNotificationSettings(true, null, null);
        givenCaregivers(relationship(1L, CareRole.OWNER), member, optedInMember);

        // when
        service(DAYTIME).notifyCaregivers(ELDERLY_ID, NotificationType.PATTERN_CHANGE, "변화", "평소와 조금 달라요");

        // then
        assertThat(savedNotifications(2)).extracting(n -> n.getRecipient().getId()).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("주보호자도 끈 알림은 받지 않지만, 응급은 끌 수 없다")
    void ownerOptOut_exceptEmergency() {
        // given
        CareRelationship owner = relationship(1L, CareRole.OWNER);
        owner.updateNotificationSettings(false, false, false);
        givenCaregivers(owner);

        // when
        service(DAYTIME).notifyCaregivers(ELDERLY_ID, NotificationType.PATTERN_CHANGE, "변화", "b");
        service(DAYTIME).notifyCaregivers(ELDERLY_ID, NotificationType.DAILY_SUMMARY, "요약", "b");
        service(DAYTIME).notifyCaregivers(ELDERLY_ID, NotificationType.EMERGENCY, "응급", "b");

        // then
        assertThat(savedNotifications(1)).extracting(Notification::getType).containsExactly(NotificationType.EMERGENCY);
    }

    @Test
    @DisplayName("22:00~07:00에는 응급이 아닌 알림의 푸시를 미뤄 두고(앱 목록에는 바로 저장) 이벤트를 내지 않는다")
    void nonEmergency_atNight_deferred() {
        // given
        givenCaregivers(relationship(1L, CareRole.OWNER));

        // when
        service(NIGHT).notifyCaregivers(ELDERLY_ID, NotificationType.PROACTIVE_NO_RESPONSE, "무응답", "응답이 없으셨어요");

        // then
        assertThat(savedNotifications(1).get(0).getPushStatus()).isEqualTo(PushStatus.DEFERRED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("받을 보호자가 없으면 아무것도 저장하지 않는다")
    void noRecipients() {
        // given
        given(careRelationshipRepository.findAllWithCaregiverByElderlyId(ELDERLY_ID))
                .willReturn(List.of(relationship(2L, CareRole.MEMBER)));

        // when
        service(DAYTIME).notifyCaregivers(ELDERLY_ID, NotificationType.DAILY_SUMMARY, "요약", "대화 3번");

        // then
        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("내 알림만 읽음 처리할 수 있다 (남의 알림은 없는 것으로 본다)")
    void markRead() {
        // given
        Notification mine = Notification.builder().type(NotificationType.DAILY_SUMMARY).title("t").body("b").build();
        given(notificationRepository.findByIdAndRecipientId(7L, 1L)).willReturn(Optional.of(mine));
        given(notificationRepository.findByIdAndRecipientId(8L, 1L)).willReturn(Optional.empty());

        // when
        service(DAYTIME).markRead(1L, 7L);

        // then
        assertThat(mine.isRead()).isTrue();
        assertThatThrownBy(() -> service(DAYTIME).markRead(1L, 8L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
    }
}
