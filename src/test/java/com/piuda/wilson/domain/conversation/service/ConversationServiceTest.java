package com.piuda.wilson.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.conversation.dto.ConversationDetailResponse;
import com.piuda.wilson.domain.conversation.dto.ConversationSummaryResponse;
import com.piuda.wilson.domain.conversation.dto.FinishConversationResponse;
import com.piuda.wilson.domain.conversation.dto.StartConversationRequest;
import com.piuda.wilson.domain.conversation.dto.StartConversationResponse;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationOrigin;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.conversation.repository.MessageRepository;
import com.piuda.wilson.domain.elderly.entity.ConsentType;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.elderly.service.ConsentService;
import com.piuda.wilson.global.common.response.PageResponse;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    private static final Long ELDERLY_MEMBER_ID = 1L;
    private static final Long CAREGIVER_MEMBER_ID = 2L;
    private static final Long ELDERLY_ID = 15L;
    private static final Long CAREGIVER_ID = 3L;
    private static final Long CONVERSATION_ID = 100L;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private CaregiverRepository caregiverRepository;

    @Mock
    private CareRelationshipRepository careRelationshipRepository;

    @Mock
    private ConversationContextService conversationContextService;

    @Mock
    private ConsentService consentService;

    @Mock
    private ConversationEndedAnnouncer conversationEndedAnnouncer;

    @InjectMocks
    private ConversationService conversationService;

    private Elderly elderly(Long id) {
        Elderly elderly = Elderly.builder().name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", id);
        return elderly;
    }

    private Conversation conversation(Elderly elderly, Long id) {
        Conversation conversation = Conversation.builder()
                .elderly(elderly).channel(ConversationChannel.VOICE).type(ConversationType.DAILY)
                .startedAt(LocalDateTime.of(2026, 10, 15, 9, 0)).build();
        ReflectionTestUtils.setField(conversation, "id", id);
        return conversation;
    }

    private Caregiver caregiver() {
        Caregiver caregiver = Caregiver.builder().build();
        ReflectionTestUtils.setField(caregiver, "id", CAREGIVER_ID);
        return caregiver;
    }

    private void givenLinkedCaregiver(boolean linked) {
        given(caregiverRepository.findByMemberId(CAREGIVER_MEMBER_ID)).willReturn(Optional.of(caregiver()));
        given(careRelationshipRepository.existsByCaregiverIdAndElderlyId(CAREGIVER_ID, ELDERLY_ID)).willReturn(linked);
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    // ===== 대화 시작 =====

    @Test
    @DisplayName("노인 기기가 대화를 시작하면 WAITING 상태의 음성 대화가 생성된다")
    void start_success() {
        // given
        Elderly elderly = elderly(ELDERLY_ID);
        given(elderlyRepository.findByMemberIdAndDeletedFalse(ELDERLY_MEMBER_ID)).willReturn(Optional.of(elderly));
        given(conversationRepository.findByElderlyIdAndStateNotInAndDeletedFalse(eq(ELDERLY_ID), any()))
                .willReturn(List.of());
        given(conversationRepository.save(any(Conversation.class))).willAnswer(invocation -> {
            Conversation saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", CONVERSATION_ID);
            return saved;
        });

        // when
        StartConversationResponse response =
                conversationService.start(ELDERLY_MEMBER_ID, new StartConversationRequest(ConversationType.MEAL));

        // then
        assertThat(response.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(response.state()).isEqualTo(ConversationState.WAITING);
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(ConversationType.MEAL);
        assertThat(captor.getValue().getChannel()).isEqualTo(ConversationChannel.VOICE);
        assertThat(captor.getValue().getOrigin()).isEqualTo(ConversationOrigin.USER);
    }

    @Test
    @DisplayName("선제 대화는 일정 유형으로, 시작 경로가 PROACTIVE인 대화로 만든다")
    void startProactive() {
        // given
        Elderly elderly = elderly(ELDERLY_ID);
        given(elderlyRepository.findByMemberIdAndDeletedFalse(ELDERLY_MEMBER_ID)).willReturn(Optional.of(elderly));
        given(conversationRepository.findByElderlyIdAndStateNotInAndDeletedFalse(eq(ELDERLY_ID), any()))
                .willReturn(List.of());
        given(conversationRepository.save(any(Conversation.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        conversationService.startProactive(ELDERLY_MEMBER_ID, ConversationType.MEDICATION);

        // then
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getOrigin()).isEqualTo(ConversationOrigin.PROACTIVE);
        assertThat(captor.getValue().getType()).isEqualTo(ConversationType.MEDICATION);
    }

    @Test
    @DisplayName("진행 중인 대화가 남아 있으면 자동 종료(맥락 삭제·AI에 종료 알림)하고 새 대화를 시작한다")
    void start_finishesActiveConversation() {
        // given
        Elderly elderly = elderly(ELDERLY_ID);
        Conversation dangling = conversation(elderly, 99L);
        given(elderlyRepository.findByMemberIdAndDeletedFalse(ELDERLY_MEMBER_ID)).willReturn(Optional.of(elderly));
        given(conversationRepository.findByElderlyIdAndStateNotInAndDeletedFalse(eq(ELDERLY_ID), any()))
                .willReturn(List.of(dangling));
        given(conversationRepository.save(any(Conversation.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        conversationService.start(ELDERLY_MEMBER_ID, new StartConversationRequest(ConversationType.DAILY));

        // then
        assertThat(dangling.getState()).isEqualTo(ConversationState.FINISHED);
        assertThat(dangling.getEndedAt()).isNotNull();
        verify(conversationContextService).clear(99L);
        verify(conversationEndedAnnouncer).announce(dangling);
    }

    @Test
    @DisplayName("노인 회원이 아니면 대화를 시작할 수 없다")
    void start_notElderly_throws() {
        // given
        given(elderlyRepository.findByMemberIdAndDeletedFalse(CAREGIVER_MEMBER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> conversationService.start(CAREGIVER_MEMBER_ID,
                new StartConversationRequest(ConversationType.DAILY)))
                .satisfies(e -> assertErrorCode(e, ErrorCode.ELDERLY_NOT_FOUND));
        verify(conversationRepository, never()).save(any());
    }

    // ===== 대화 종료 =====

    @Test
    @DisplayName("노인 기기가 자신의 대화를 종료하면 대화 맥락을 지우고 AI 워커에 종료를 알린다 (J2)")
    void finish_success() {
        // given
        Elderly elderly = elderly(ELDERLY_ID);
        Conversation conversation = conversation(elderly, CONVERSATION_ID);
        given(elderlyRepository.findByMemberIdAndDeletedFalse(ELDERLY_MEMBER_ID)).willReturn(Optional.of(elderly));
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.of(conversation));

        // when
        FinishConversationResponse response = conversationService.finish(ELDERLY_MEMBER_ID, CONVERSATION_ID);

        // then
        assertThat(response.state()).isEqualTo(ConversationState.FINISHED);
        assertThat(response.endedAt()).isNotNull();
        verify(conversationContextService).clear(CONVERSATION_ID);
        verify(conversationEndedAnnouncer).announce(conversation);
    }

    @Test
    @DisplayName("이미 종료된 대화는 다시 종료할 수 없다")
    void finish_alreadyFinished_throws() {
        // given
        Elderly elderly = elderly(ELDERLY_ID);
        Conversation conversation = conversation(elderly, CONVERSATION_ID);
        conversation.finish(LocalDateTime.of(2026, 10, 15, 9, 30));
        given(elderlyRepository.findByMemberIdAndDeletedFalse(ELDERLY_MEMBER_ID)).willReturn(Optional.of(elderly));
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.of(conversation));

        // when & then
        assertThatThrownBy(() -> conversationService.finish(ELDERLY_MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.CONVERSATION_ALREADY_FINISHED));
        assertThat(conversation.getEndedAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 9, 30));
        verify(conversationEndedAnnouncer, never()).announce(any());
    }

    @Test
    @DisplayName("없는 대화는 종료할 수 없다")
    void finish_notFound_throws() {
        // given
        given(elderlyRepository.findByMemberIdAndDeletedFalse(ELDERLY_MEMBER_ID))
                .willReturn(Optional.of(elderly(ELDERLY_ID)));
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> conversationService.finish(ELDERLY_MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Test
    @DisplayName("다른 노인의 대화는 종료할 수 없다")
    void finish_notOwner_throws() {
        // given
        Conversation othersConversation = conversation(elderly(77L), CONVERSATION_ID);
        given(elderlyRepository.findByMemberIdAndDeletedFalse(ELDERLY_MEMBER_ID))
                .willReturn(Optional.of(elderly(ELDERLY_ID)));
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID))
                .willReturn(Optional.of(othersConversation));

        // when & then
        assertThatThrownBy(() -> conversationService.finish(ELDERLY_MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
        assertThat(othersConversation.isFinished()).isFalse();
    }

    // ===== 대화 목록 =====

    @Test
    @DisplayName("연결된 보호자가 노인의 대화 목록을 페이지로 조회한다")
    void getConversations_success() {
        // given
        Elderly elderly = elderly(ELDERLY_ID);
        Pageable pageable = PageRequest.of(0, 20);
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly));
        givenLinkedCaregiver(true);
        given(conversationRepository.findByElderlyIdAndDeletedFalseOrderByStartedAtDescIdDesc(ELDERLY_ID, pageable))
                .willReturn(new PageImpl<>(List.of(conversation(elderly, CONVERSATION_ID)), pageable, 1));

        // when
        PageResponse<ConversationSummaryResponse> response =
                conversationService.getConversations(CAREGIVER_MEMBER_ID, ELDERLY_ID, null, pageable);

        // then
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().get(0).conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(response.content().get(0).conversationType()).isEqualTo(ConversationType.DAILY);
    }

    @Test
    @DisplayName("날짜를 지정하면 그날 0시부터 다음날 0시 전까지 시작한 대화를 조회한다")
    void getConversations_onDate() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(ELDERLY_ID)));
        givenLinkedCaregiver(true);
        given(conversationRepository.findPageByElderlyIdAndStartedAtRange(ELDERLY_ID,
                LocalDateTime.of(2026, 10, 15, 0, 0), LocalDateTime.of(2026, 10, 16, 0, 0), pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        PageResponse<ConversationSummaryResponse> response = conversationService.getConversations(
                CAREGIVER_MEMBER_ID, ELDERLY_ID, LocalDate.of(2026, 10, 15), pageable);

        // then
        assertThat(response.content()).isEmpty();
    }

    @Test
    @DisplayName("연결되지 않은 보호자는 대화 목록을 조회할 수 없다")
    void getConversations_noPermission_throws() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(ELDERLY_ID)));
        givenLinkedCaregiver(false);

        // when & then
        assertThatThrownBy(() -> conversationService.getConversations(
                CAREGIVER_MEMBER_ID, ELDERLY_ID, null, PageRequest.of(0, 20)))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("삭제된 노인의 대화 목록은 조회할 수 없다")
    void getConversations_elderlyNotFound_throws() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> conversationService.getConversations(
                CAREGIVER_MEMBER_ID, ELDERLY_ID, null, PageRequest.of(0, 20)))
                .satisfies(e -> assertErrorCode(e, ErrorCode.ELDERLY_NOT_FOUND));
    }

    // ===== 대화 상세 =====

    @Test
    @DisplayName("원문 공유 동의가 있으면 연결된 보호자가 대화 상세와 메시지를 시간순으로 조회한다")
    void getConversation_success() {
        // given
        Conversation conversation = conversation(elderly(ELDERLY_ID), CONVERSATION_ID);
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.of(conversation));
        givenLinkedCaregiver(true);
        given(consentService.isAgreed(ELDERLY_ID, ConsentType.TRANSCRIPT_SHARING)).willReturn(true);
        given(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(CONVERSATION_ID)).willReturn(List.of(
                Message.builder().conversation(conversation).sender(MessageSender.AI).content("식사는 하셨어요?").build(),
                Message.builder().conversation(conversation).sender(MessageSender.ELDERLY).content("응 먹었어").build()));

        // when
        ConversationDetailResponse response = conversationService.getConversation(CAREGIVER_MEMBER_ID, CONVERSATION_ID);

        // then
        assertThat(response.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(response.messages()).extracting(ConversationDetailResponse.MessageResponse::text)
                .containsExactly("식사는 하셨어요?", "응 먹었어");
        assertThat(response.messages().get(0).role()).isEqualTo(MessageSender.AI);
        assertThat(response.transcriptVisible()).isTrue();
    }

    @Test
    @DisplayName("원문 공유 동의가 없으면 대화 정보와 AI 요약만 주고 원문은 읽지도 않는다 (요약·지표만 공개)")
    void getConversation_withoutTranscriptConsent() {
        // given
        Conversation conversation = conversation(elderly(ELDERLY_ID), CONVERSATION_ID);
        conversation.updateSummary("점심으로 된장찌개를 드셨어요.");
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.of(conversation));
        givenLinkedCaregiver(true);
        given(consentService.isAgreed(ELDERLY_ID, ConsentType.TRANSCRIPT_SHARING)).willReturn(false);

        // when
        ConversationDetailResponse response = conversationService.getConversation(CAREGIVER_MEMBER_ID, CONVERSATION_ID);

        // then
        assertThat(response.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(response.transcriptVisible()).isFalse();
        assertThat(response.messages()).isEmpty();
        assertThat(response.summary()).isEqualTo("점심으로 된장찌개를 드셨어요.");
        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtAscIdAsc(anyLong());
    }

    @Test
    @DisplayName("연결되지 않은 보호자는 대화 상세를 조회할 수 없다")
    void getConversation_noPermission_throws() {
        // given
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID))
                .willReturn(Optional.of(conversation(elderly(ELDERLY_ID), CONVERSATION_ID)));
        givenLinkedCaregiver(false);

        // when & then
        assertThatThrownBy(() -> conversationService.getConversation(CAREGIVER_MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtAscIdAsc(anyLong());
    }

    @Test
    @DisplayName("삭제된 노인의 대화 상세는 조회할 수 없다")
    void getConversation_deletedElderly_throws() {
        // given
        Elderly deletedElderly = elderly(ELDERLY_ID);
        deletedElderly.delete();
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID))
                .willReturn(Optional.of(conversation(deletedElderly, CONVERSATION_ID)));

        // when & then
        assertThatThrownBy(() -> conversationService.getConversation(CAREGIVER_MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.CONVERSATION_NOT_FOUND));
    }

    // ===== 대화 삭제 =====

    @Test
    @DisplayName("연결된 보호자가 대화를 삭제하면 Soft Delete 된다")
    void delete_success() {
        // given
        Conversation conversation = conversation(elderly(ELDERLY_ID), CONVERSATION_ID);
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.of(conversation));
        givenLinkedCaregiver(true);

        // when
        conversationService.delete(CAREGIVER_MEMBER_ID, CONVERSATION_ID);

        // then
        assertThat(conversation.isDeleted()).isTrue();
        verify(conversationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("연결되지 않은 보호자는 대화를 삭제할 수 없다")
    void delete_noPermission_throws() {
        // given
        Conversation conversation = conversation(elderly(ELDERLY_ID), CONVERSATION_ID);
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.of(conversation));
        givenLinkedCaregiver(false);

        // when & then
        assertThatThrownBy(() -> conversationService.delete(CAREGIVER_MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
        assertThat(conversation.isDeleted()).isFalse();
    }
}
