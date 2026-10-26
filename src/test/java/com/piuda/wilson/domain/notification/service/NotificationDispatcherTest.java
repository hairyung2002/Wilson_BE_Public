package com.piuda.wilson.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.notification.entity.Notification;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.entity.PushPlatform;
import com.piuda.wilson.domain.notification.entity.PushStatus;
import com.piuda.wilson.domain.notification.entity.PushToken;
import com.piuda.wilson.domain.notification.repository.NotificationRepository;
import com.piuda.wilson.domain.notification.repository.PushTokenRepository;
import com.piuda.wilson.infrastructure.push.PushMessage;
import com.piuda.wilson.infrastructure.push.PushResult;
import com.piuda.wilson.infrastructure.push.PushSender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final Clock MORNING = Clock.fixed(Instant.parse("2026-10-20T22:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private PushSender pushSender;

    private NotificationDispatcher dispatcher;

    private final Member owner = withId(Member.builder().name("보호자").memberType(MemberType.CAREGIVER).build(), 1L);
    private final Elderly elderly = withId(Elderly.builder().name("김철수").build(), 15L);
    private final PushToken token = PushToken.builder().member(owner).token("device").platform(PushPlatform.IOS).build();

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(notificationRepository, pushTokenRepository, pushSender, MORNING);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private Notification notification(Long id, PushStatus status) {
        return withId(Notification.builder().recipient(owner).elderly(elderly).type(NotificationType.PATTERN_CHANGE)
                .title("변화").body("평소와 조금 달라요").pushStatus(status).build(), id);
    }

    @Test
    @DisplayName("보호자 기기로 푸시를 보내고 보낸 시각을 남긴다")
    void dispatch_sent() {
        // given
        Notification notification = notification(100L, PushStatus.QUEUED);
        given(notificationRepository.findAllById(List.of(100L))).willReturn(List.of(notification));
        given(pushTokenRepository.findByMemberId(1L)).willReturn(Optional.of(token));
        given(pushSender.send(any())).willReturn(PushResult.SENT);

        // when
        dispatcher.dispatch(List.of(100L));

        // then
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(captor.capture());
        assertThat(captor.getValue().token()).isEqualTo("device");
        assertThat(captor.getValue().title()).isEqualTo("변화");
        assertThat(captor.getValue().data()).containsEntry("type", "PATTERN_CHANGE").containsEntry("elderlyId", "15");
        assertThat(notification.getPushStatus()).isEqualTo(PushStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("기기가 등록되지 않았으면 보내지 않고 기기 없음으로 남긴다 (앱 목록에는 있음)")
    void dispatch_noDevice() {
        // given
        Notification notification = notification(100L, PushStatus.QUEUED);
        given(notificationRepository.findAllById(List.of(100L))).willReturn(List.of(notification));
        given(pushTokenRepository.findByMemberId(1L)).willReturn(Optional.empty());

        // when
        dispatcher.dispatch(List.of(100L));

        // then
        verify(pushSender, never()).send(any());
        assertThat(notification.getPushStatus()).isEqualTo(PushStatus.NO_DEVICE);
    }

    @Test
    @DisplayName("FCM이 등록되지 않은 토큰이라고 하면 그 토큰을 지운다")
    void dispatch_invalidToken() {
        // given
        Notification notification = notification(100L, PushStatus.QUEUED);
        given(notificationRepository.findAllById(List.of(100L))).willReturn(List.of(notification));
        given(pushTokenRepository.findByMemberId(1L)).willReturn(Optional.of(token));
        given(pushSender.send(any())).willReturn(PushResult.INVALID_TOKEN);

        // when
        dispatcher.dispatch(List.of(100L));

        // then
        verify(pushTokenRepository).delete(token);
        assertThat(notification.getPushStatus()).isEqualTo(PushStatus.NO_DEVICE);
    }

    @Test
    @DisplayName("일시 오류는 3회까지 다시 보내고, 그래도 실패하면 실패로 남긴다")
    void dispatch_retry() {
        // given
        Notification notification = notification(100L, PushStatus.QUEUED);
        given(notificationRepository.findAllById(List.of(100L))).willReturn(List.of(notification));
        given(pushTokenRepository.findByMemberId(1L)).willReturn(Optional.of(token));
        given(pushSender.send(any())).willReturn(PushResult.FAILED);

        // when
        dispatcher.dispatch(List.of(100L));

        // then
        verify(pushSender, times(3)).send(any());
        assertThat(notification.getPushStatus()).isEqualTo(PushStatus.FAILED);
    }

    @Test
    @DisplayName("밤사이 미뤄 둔 알림은 아침에 보호자·노인별로 묶어 한 번만 보낸다")
    void dispatchDeferred_grouped() {
        // given
        Notification first = notification(100L, PushStatus.DEFERRED);
        Notification second = notification(101L, PushStatus.DEFERRED);
        given(notificationRepository.findByPushStatusOrderByIdAsc(PushStatus.DEFERRED)).willReturn(List.of(first, second));
        given(pushTokenRepository.findByMemberId(1L)).willReturn(Optional.of(token));
        given(pushSender.send(any())).willReturn(PushResult.SENT);

        // when
        dispatcher.dispatchDeferred();

        // then
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(captor.capture());
        assertThat(captor.getValue().body()).isEqualTo("김철수님 관련 새 알림 2건이 있어요");
        assertThat(first.getPushStatus()).isEqualTo(PushStatus.SENT);
        assertThat(second.getPushStatus()).isEqualTo(PushStatus.SENT);
    }

    @Test
    @DisplayName("미뤄 둔 알림이 1건이면 그 알림 내용 그대로 보낸다")
    void dispatchDeferred_single() {
        // given
        given(notificationRepository.findByPushStatusOrderByIdAsc(PushStatus.DEFERRED))
                .willReturn(List.of(notification(100L, PushStatus.DEFERRED)));
        given(pushTokenRepository.findByMemberId(1L)).willReturn(Optional.of(token));
        given(pushSender.send(any())).willReturn(PushResult.SENT);

        // when
        dispatcher.dispatchDeferred();

        // then
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(captor.capture());
        assertThat(captor.getValue().body()).isEqualTo("평소와 조금 달라요");
    }
}
