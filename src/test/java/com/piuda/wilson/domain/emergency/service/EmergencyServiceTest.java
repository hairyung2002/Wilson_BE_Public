package com.piuda.wilson.domain.emergency.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.service.AccessActor;
import com.piuda.wilson.domain.elderly.service.ElderlyAccess;
import com.piuda.wilson.domain.elderly.service.ElderlyAccessService;
import com.piuda.wilson.domain.emergency.dto.EmergencyResponse;
import com.piuda.wilson.domain.emergency.entity.Emergency;
import com.piuda.wilson.domain.emergency.repository.EmergencyRepository;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.member.repository.MemberRepository;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.service.NotificationService;
import com.piuda.wilson.global.common.response.PageResponse;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {

    /** 2026-10-20 08:00 KST */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T23:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 0);
    private static final Long ELDERLY_ID = 15L;
    private static final Long CONVERSATION_ID = 100L;
    private static final Long MEMBER_ID = 3L;

    @Mock
    private EmergencyRepository emergencyRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ElderlyAccessService elderlyAccessService;

    @Mock
    private NotificationService notificationService;

    private EmergencyService emergencyService;
    private Elderly elderly;

    @BeforeEach
    void setUp() {
        emergencyService = new EmergencyService(emergencyRepository, conversationRepository, memberRepository,
                elderlyAccessService, notificationService, CLOCK);
        elderly = Elderly.builder().name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", ELDERLY_ID);
    }

    private void givenConversation() {
        Conversation conversation = Conversation.builder().elderly(elderly).channel(ConversationChannel.VOICE)
                .type(ConversationType.DAILY).startedAt(NOW.minusMinutes(3)).build();
        given(conversationRepository.findById(CONVERSATION_ID)).willReturn(Optional.of(conversation));
    }

    private Emergency emergency(EmergencyCategory category) {
        Emergency emergency = Emergency.builder().elderly(elderly).conversationId(CONVERSATION_ID).turnId("turn-1")
                .category(category).detectedAt(NOW.minusMinutes(1)).notified(true).build();
        ReflectionTestUtils.setField(emergency, "id", 7L);
        return emergency;
    }

    private Member member(Long id, String name) {
        Member member = Member.builder().name(name).memberType(MemberType.CAREGIVER).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    @Test
    @DisplayName("응급을 기록하고, 연결된 보호자 전원에게 대화 원문 없이 바로 알린다")
    void report() {
        // given
        givenConversation();
        given(emergencyRepository.existsByElderlyIdAndCategoryAndNotifiedTrueAndDetectedAtGreaterThanEqual(
                ELDERLY_ID, EmergencyCategory.FALL, NOW.minusMinutes(10))).willReturn(false);

        // when
        emergencyService.report(CONVERSATION_ID, "turn-1", EmergencyCategory.FALL);

        // then
        ArgumentCaptor<Emergency> captor = ArgumentCaptor.forClass(Emergency.class);
        verify(emergencyRepository).save(captor.capture());
        Emergency saved = captor.getValue();
        assertThat(saved.getElderly()).isSameAs(elderly);
        assertThat(saved.getConversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(saved.getTurnId()).isEqualTo("turn-1");
        assertThat(saved.getDetectedAt()).isEqualTo(NOW);
        assertThat(saved.isNotified()).isTrue();
        verify(notificationService).notifyCaregivers(ELDERLY_ID, NotificationType.EMERGENCY, "김철수님 확인이 필요해요",
                "김철수님 대화에서 넘어지셨다는 말씀이 있었어요. 바로 연락해 보세요");
    }

    @Test
    @DisplayName("10분 안에 같은 종류가 이미 알려졌으면 기록만 하고 다시 알리지 않는다")
    void report_duplicateWithinTenMinutes() {
        // given
        givenConversation();
        given(emergencyRepository.existsByElderlyIdAndCategoryAndNotifiedTrueAndDetectedAtGreaterThanEqual(
                ELDERLY_ID, EmergencyCategory.PAIN, NOW.minusMinutes(10))).willReturn(true);

        // when
        emergencyService.report(CONVERSATION_ID, "turn-2", EmergencyCategory.PAIN);

        // then
        ArgumentCaptor<Emergency> captor = ArgumentCaptor.forClass(Emergency.class);
        verify(emergencyRepository).save(captor.capture());
        assertThat(captor.getValue().isNotified()).isFalse();
        verify(notificationService, never()).notifyCaregivers(any(), any(), any(), any());
    }

    @Test
    @DisplayName("응급 목록을 최신순 페이지로, 누가 확인했는지와 함께 돌려준다")
    void getEmergencies() {
        // given
        given(elderlyAccessService.requireCaregiver(MEMBER_ID, ELDERLY_ID))
                .willReturn(new ElderlyAccess(elderly, AccessActor.MEMBER));
        Emergency acknowledged = emergency(EmergencyCategory.FALL);
        acknowledged.acknowledge(member(MEMBER_ID, "딸"), NOW);
        given(emergencyRepository.findByElderlyIdOrderByDetectedAtDesc(ELDERLY_ID, PageRequest.of(0, 20)))
                .willReturn(new PageImpl<>(List.of(acknowledged), PageRequest.of(0, 20), 1));

        // when
        PageResponse<EmergencyResponse> page = emergencyService.getEmergencies(MEMBER_ID, ELDERLY_ID,
                PageRequest.of(0, 20));

        // then
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).containsExactly(new EmergencyResponse(7L, EmergencyCategory.FALL,
                NOW.minusMinutes(1), NOW, "딸"));
    }

    @Test
    @DisplayName("보호자 한 명이 확인하면 확인됨으로 남고, 나중에 다른 보호자가 눌러도 처음 확인한 사람이 유지된다")
    void acknowledge() {
        // given
        Emergency emergency = emergency(EmergencyCategory.FALL);
        given(emergencyRepository.findById(7L)).willReturn(Optional.of(emergency));
        given(elderlyAccessService.requireCaregiver(MEMBER_ID, ELDERLY_ID))
                .willReturn(new ElderlyAccess(elderly, AccessActor.OWNER));
        given(elderlyAccessService.requireCaregiver(4L, ELDERLY_ID))
                .willReturn(new ElderlyAccess(elderly, AccessActor.MEMBER));
        given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member(MEMBER_ID, "아들"));

        // when
        emergencyService.acknowledge(MEMBER_ID, 7L);
        emergencyService.acknowledge(4L, 7L);

        // then
        assertThat(emergency.getAcknowledgedAt()).isEqualTo(NOW);
        assertThat(emergency.getAcknowledgedBy().getName()).isEqualTo("아들");
        verify(memberRepository, never()).getReferenceById(4L);
    }

    @Test
    @DisplayName("없는 응급 기록이면 404 EM001")
    void acknowledge_notFound() {
        // given
        given(emergencyRepository.findById(7L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> emergencyService.acknowledge(MEMBER_ID, 7L))
                .satisfies(e -> assertErrorCode(e, ErrorCode.EMERGENCY_NOT_FOUND));
    }

    @Test
    @DisplayName("연결되지 않은 보호자는 확인할 수 없다")
    void acknowledge_forbidden() {
        // given
        given(emergencyRepository.findById(7L)).willReturn(Optional.of(emergency(EmergencyCategory.FALL)));
        given(elderlyAccessService.requireCaregiver(MEMBER_ID, ELDERLY_ID))
                .willThrow(new CustomException(ErrorCode.NO_PERMISSION));

        // when & then
        assertThatThrownBy(() -> emergencyService.acknowledge(MEMBER_ID, 7L))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }
}
