package com.piuda.wilson.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.conversation.dto.StartConversationResponse;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.ProactiveStatus;
import com.piuda.wilson.domain.conversation.entity.ProactiveTrigger;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.conversation.repository.ProactiveTriggerRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.entity.Gender;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.entity.PushPlatform;
import com.piuda.wilson.domain.notification.entity.PushToken;
import com.piuda.wilson.domain.notification.repository.PushTokenRepository;
import com.piuda.wilson.domain.notification.service.NotificationService;
import com.piuda.wilson.domain.schedule.entity.Schedule;
import com.piuda.wilson.domain.schedule.entity.ScheduleAuthor;
import com.piuda.wilson.domain.schedule.entity.ScheduleType;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.infrastructure.push.PushMessage;
import com.piuda.wilson.infrastructure.push.PushResult;
import com.piuda.wilson.infrastructure.push.PushSender;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
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
class ProactiveServiceTest {

    private static final Long ELDERLY_MEMBER_ID = 7L;
    /** 2026-10-20 08:50 KST */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T23:50:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private ProactiveTriggerRepository proactiveTriggerRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private PushSender pushSender;

    @Mock
    private NotificationService notificationService;

    private ProactiveService proactiveService;

    private final Member elderlyMember = withId(Member.builder().name("김철수").memberType(MemberType.ELDERLY).build(),
            ELDERLY_MEMBER_ID);
    private final Elderly elderly = withId(Elderly.builder().member(elderlyMember).name("김철수")
            .gender(Gender.FEMALE).build(), 15L);
    private static final String GREETING = "할머니, 혈압약 드실 시간이에요. 챙겨 드셨어요?";
    private final PushToken token = PushToken.builder().member(elderlyMember).token("elder-device")
            .platform(PushPlatform.ANDROID).build();

    @BeforeEach
    void setUp() {
        proactiveService = new ProactiveService(proactiveTriggerRepository, conversationRepository,
                conversationService, pushTokenRepository, pushSender, notificationService, CLOCK);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private Schedule schedule(String label) {
        return withId(Schedule.builder().elderly(elderly).type(ScheduleType.MEDICATION).label(label)
                .time(LocalTime.of(8, 30)).daysOfWeek(EnumSet.of(DayOfWeek.TUESDAY)).enabled(true)
                .createdBy(ScheduleAuthor.CAREGIVER).build(), 3L);
    }

    private ProactiveTrigger trigger(ProactiveStatus status, LocalDateTime sentAt) {
        ProactiveTrigger trigger = ProactiveTrigger.create(schedule("혈압약"), sentAt, GREETING);
        ReflectionTestUtils.setField(trigger, "status", status);
        return trigger;
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    // ===== 선제 대화 걸기 =====

    @Test
    @DisplayName("일정 시각이 되면 호칭·일정으로 첫 안부를 만들어 기록하고, 노인 기기에 그 안부를 알림으로 보낸다 (J4)")
    void trigger() {
        // given
        given(pushTokenRepository.findByMemberId(ELDERLY_MEMBER_ID)).willReturn(Optional.of(token));
        given(pushSender.send(any())).willReturn(PushResult.SENT);

        // when
        proactiveService.trigger(schedule("혈압약"), NOW);

        // then
        ArgumentCaptor<ProactiveTrigger> saved = ArgumentCaptor.forClass(ProactiveTrigger.class);
        verify(proactiveTriggerRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ProactiveStatus.SENT);
        assertThat(saved.getValue().getConversationType()).isEqualTo(ConversationType.MEDICATION);
        assertThat(saved.getValue().getGreeting()).isEqualTo(GREETING);
        ArgumentCaptor<PushMessage> push = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(push.capture());
        assertThat(push.getValue().token()).isEqualTo("elder-device");
        assertThat(push.getValue().title()).isEqualTo("윌슨");
        assertThat(push.getValue().body()).isEqualTo(GREETING);
        assertThat(push.getValue().data()).containsOnly(
                java.util.Map.entry("type", "PROACTIVE"),
                java.util.Map.entry("triggerId", saved.getValue().getId()),
                java.util.Map.entry("conversationType", "MEDICATION"),
                java.util.Map.entry("greeting", GREETING));
    }

    @Test
    @DisplayName("노인 기기가 등록되지 않았으면 보낼 수 없음으로 남긴다 (보호자 무응답 알림 대상 아님)")
    void trigger_noDevice() {
        // given
        given(pushTokenRepository.findByMemberId(ELDERLY_MEMBER_ID)).willReturn(Optional.empty());

        // when
        proactiveService.trigger(schedule(null), NOW);

        // then
        ArgumentCaptor<ProactiveTrigger> saved = ArgumentCaptor.forClass(ProactiveTrigger.class);
        verify(proactiveTriggerRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ProactiveStatus.UNDELIVERABLE);
        verify(pushSender, never()).send(any());
    }

    // ===== 6-2 앱이 대화 시작 =====

    @Test
    @DisplayName("푸시를 받은 노인 앱이 트리거로 선제 대화를 만든다")
    void start() {
        // given
        ProactiveTrigger trigger = trigger(ProactiveStatus.SENT, NOW.minusMinutes(5));
        given(proactiveTriggerRepository.findById(trigger.getId())).willReturn(Optional.of(trigger));
        given(conversationService.startProactive(ELDERLY_MEMBER_ID, ConversationType.MEDICATION))
                .willReturn(new StartConversationResponse(100L, ConversationState.WAITING));

        // when
        StartConversationResponse response = proactiveService.start(ELDERLY_MEMBER_ID, trigger.getId());

        // then
        assertThat(response.conversationId()).isEqualTo(100L);
        assertThat(trigger.getConversationId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("재푸시로 같은 트리거가 다시 오면 이미 만든 대화를 그대로 돌려준다")
    void start_again() {
        // given
        ProactiveTrigger trigger = trigger(ProactiveStatus.RETRIED, NOW.minusMinutes(12));
        trigger.attachConversation(100L);
        Conversation conversation = withId(Conversation.builder().elderly(elderly).channel(ConversationChannel.VOICE)
                .type(ConversationType.MEDICATION).startedAt(NOW.minusMinutes(11)).build(), 100L);
        given(proactiveTriggerRepository.findById(trigger.getId())).willReturn(Optional.of(trigger));
        given(conversationRepository.findByIdAndDeletedFalse(100L)).willReturn(Optional.of(conversation));

        // when & then
        assertThat(proactiveService.start(ELDERLY_MEMBER_ID, trigger.getId()).conversationId()).isEqualTo(100L);
        verify(conversationService, never()).startProactive(any(), any());
    }

    @Test
    @DisplayName("30분이 지났거나 이미 끝난 트리거로는 대화를 만들 수 없다")
    void start_expired() {
        // given
        ProactiveTrigger expired = trigger(ProactiveStatus.RETRIED, NOW.minusMinutes(31));
        ProactiveTrigger missed = trigger(ProactiveStatus.MISSED, NOW.minusMinutes(5));
        given(proactiveTriggerRepository.findById(expired.getId())).willReturn(Optional.of(expired));
        given(proactiveTriggerRepository.findById(missed.getId())).willReturn(Optional.of(missed));

        // when & then
        assertThatThrownBy(() -> proactiveService.start(ELDERLY_MEMBER_ID, expired.getId()))
                .satisfies(e -> assertErrorCode(e, ErrorCode.PROACTIVE_TRIGGER_UNAVAILABLE));
        assertThatThrownBy(() -> proactiveService.start(ELDERLY_MEMBER_ID, missed.getId()))
                .satisfies(e -> assertErrorCode(e, ErrorCode.PROACTIVE_TRIGGER_UNAVAILABLE));
    }

    @Test
    @DisplayName("다른 노인의 트리거면 403, 없는 트리거면 404")
    void start_otherOrMissing() {
        // given
        ProactiveTrigger trigger = trigger(ProactiveStatus.SENT, NOW.minusMinutes(5));
        given(proactiveTriggerRepository.findById(trigger.getId())).willReturn(Optional.of(trigger));
        given(proactiveTriggerRepository.findById("nope")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> proactiveService.start(99L, trigger.getId()))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
        assertThatThrownBy(() -> proactiveService.start(ELDERLY_MEMBER_ID, "nope"))
                .satisfies(e -> assertErrorCode(e, ErrorCode.PROACTIVE_TRIGGER_NOT_FOUND));
    }

    // ===== 응답 확인 =====

    @Test
    @DisplayName("선제 대화에서 노인이 말하면 응답한 것으로 기록한다")
    void markResponded() {
        // given
        ProactiveTrigger trigger = trigger(ProactiveStatus.RETRIED, NOW.minusMinutes(12));
        given(proactiveTriggerRepository.findByConversationId(100L)).willReturn(Optional.of(trigger));

        // when
        proactiveService.markResponded(100L);

        // then
        assertThat(trigger.getStatus()).isEqualTo(ProactiveStatus.RESPONDED);
    }

    @Test
    @DisplayName("10분 동안 응답이 없으면 푸시를 한 번 더 보내고, 다시 10분이 지나면 보호자에게 무응답을 알린다")
    void followUp() {
        // given
        ProactiveTrigger unanswered = trigger(ProactiveStatus.SENT, NOW.minusMinutes(10));
        ProactiveTrigger stillUnanswered = trigger(ProactiveStatus.RETRIED, NOW.minusMinutes(20));
        ReflectionTestUtils.setField(stillUnanswered, "retriedAt", NOW.minusMinutes(10));
        given(proactiveTriggerRepository.findByStatusAndSentAtLessThanEqual(ProactiveStatus.SENT, NOW.minusMinutes(10)))
                .willReturn(List.of(unanswered));
        given(proactiveTriggerRepository.findByStatusAndRetriedAtLessThanEqual(ProactiveStatus.RETRIED,
                NOW.minusMinutes(10))).willReturn(List.of(stillUnanswered));
        given(pushTokenRepository.findByMemberId(ELDERLY_MEMBER_ID)).willReturn(Optional.of(token));
        given(pushSender.send(any())).willReturn(PushResult.SENT);

        // when
        proactiveService.followUp();

        // then
        assertThat(unanswered.getStatus()).isEqualTo(ProactiveStatus.RETRIED);
        assertThat(unanswered.getRetriedAt()).isEqualTo(NOW);
        ArgumentCaptor<PushMessage> push = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(push.capture());
        assertThat(push.getValue().body()).as("재푸시도 같은 안부를 보낸다").isEqualTo(GREETING);
        assertThat(stillUnanswered.getStatus()).isEqualTo(ProactiveStatus.MISSED);
        verify(notificationService).notifyCaregivers(15L, NotificationType.PROACTIVE_NO_RESPONSE,
                "김철수님 선제 대화", "08:30 혈압약 안부에 응답이 없으셨어요");
    }
}
