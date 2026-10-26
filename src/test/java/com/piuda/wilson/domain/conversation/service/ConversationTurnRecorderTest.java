package com.piuda.wilson.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationOrigin;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import com.piuda.wilson.domain.conversation.entity.ProactiveTrigger;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.conversation.repository.MessageRepository;
import com.piuda.wilson.domain.conversation.repository.ProactiveTriggerRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConversationTurnRecorderTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ELDERLY_ID = 15L;
    private static final Long CONVERSATION_ID = 100L;
    private static final String TURN_ID = "turn-1";
    private static final String GREETING = "어르신, 오늘 점심은 뭐 드셨어요?";

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private ProactiveTriggerRepository proactiveTriggerRepository;

    @InjectMocks
    private ConversationTurnRecorder recorder;

    private Elderly elderly(Long id) {
        Elderly elderly = Elderly.builder().name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", id);
        return elderly;
    }

    private Conversation conversation(Elderly elderly) {
        Conversation conversation = Conversation.builder()
                .elderly(elderly).channel(ConversationChannel.VOICE).type(ConversationType.DAILY)
                .startedAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(conversation, "id", CONVERSATION_ID);
        return conversation;
    }

    private Conversation givenOwnConversation() {
        Elderly elderly = elderly(ELDERLY_ID);
        Conversation conversation = conversation(elderly);
        given(elderlyRepository.findByMemberIdAndDeletedFalse(MEMBER_ID)).willReturn(Optional.of(elderly));
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.of(conversation));
        return conversation;
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    @Test
    @DisplayName("턴을 시작하면 PROCESSING 상태가 되고 새 turnId와 노인의 가명 식별자를 담는다")
    void beginTurn_success() {
        // given
        Conversation conversation = givenOwnConversation();

        // when
        VoiceTurn turn = recorder.beginTurn(MEMBER_ID, CONVERSATION_ID);

        // then
        assertThat(conversation.getState()).isEqualTo(ConversationState.PROCESSING);
        assertThat(turn.subjectId()).isEqualTo(conversation.getElderly().getSubjectId().toString());
        assertThat(turn.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(turn.turnId()).hasSize(36);
        assertThat(turn.traceId()).hasSize(36).isNotEqualTo(turn.turnId());
        assertThat(turn.addressTerm()).as("성별을 모르면 어르신").isEqualTo("어르신");
    }

    @Test
    @DisplayName("이전 턴이 처리 중이면 새 턴을 시작할 수 없다")
    void beginTurn_inProgress_throws() {
        // given
        givenOwnConversation().startTurn();

        // when & then
        assertThatThrownBy(() -> recorder.beginTurn(MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.CONVERSATION_TURN_IN_PROGRESS));
    }

    private Conversation givenOwnProactiveConversation() {
        Elderly elderly = elderly(ELDERLY_ID);
        Conversation proactive = Conversation.builder().elderly(elderly).channel(ConversationChannel.VOICE)
                .type(ConversationType.MEAL).origin(ConversationOrigin.PROACTIVE).startedAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(proactive, "id", CONVERSATION_ID);
        given(elderlyRepository.findByMemberIdAndDeletedFalse(MEMBER_ID)).willReturn(Optional.of(elderly));
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.of(proactive));
        return proactive;
    }

    @Test
    @DisplayName("선제 대화 첫 안부는 트리거의 문장을 AI 발화로 남기고, 읽는 동안 RESPONDING 상태가 된다 (J4)")
    void beginGreeting() {
        // given
        Conversation conversation = givenOwnProactiveConversation();
        ProactiveTrigger trigger = mock(ProactiveTrigger.class);
        given(trigger.getGreeting()).willReturn(GREETING);
        given(proactiveTriggerRepository.findByConversationId(CONVERSATION_ID)).willReturn(Optional.of(trigger));
        given(messageRepository.existsByConversationId(CONVERSATION_ID)).willReturn(false);

        // when
        GreetingTurn turn = recorder.beginGreeting(MEMBER_ID, CONVERSATION_ID);

        // then
        assertThat(turn.text()).isEqualTo(GREETING);
        assertThat(turn.subjectId()).isEqualTo(conversation.getElderly().getSubjectId().toString());
        assertThat(turn.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(turn.turnId()).hasSize(36);
        assertThat(turn.traceId()).hasSize(36);
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getSender()).isEqualTo(MessageSender.AI);
        assertThat(captor.getValue().getContent()).isEqualTo(GREETING);
        assertThat(captor.getValue().getTurnId()).isEqualTo(turn.turnId());
        assertThat(conversation.getState()).isEqualTo(ConversationState.RESPONDING);
    }

    @Test
    @DisplayName("이미 말이 오간 선제 대화에서 다시 첫 안부를 요청하면 프로토콜 위반이다")
    void beginGreeting_alreadyStarted() {
        // given
        givenOwnProactiveConversation();
        given(messageRepository.existsByConversationId(CONVERSATION_ID)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> recorder.beginGreeting(MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.VOICE_PROTOCOL_VIOLATION));
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("일반 대화에서 첫 안부를 요청하면 프로토콜 위반이다")
    void beginGreeting_notProactive() {
        // given
        givenOwnConversation();

        // when & then
        assertThatThrownBy(() -> recorder.beginGreeting(MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.VOICE_PROTOCOL_VIOLATION));
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("종료된 대화에서는 턴을 시작할 수 없다")
    void beginTurn_finished_throws() {
        // given
        givenOwnConversation().finish(LocalDateTime.now());

        // when & then
        assertThatThrownBy(() -> recorder.beginTurn(MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.CONVERSATION_ALREADY_FINISHED));
    }

    @Test
    @DisplayName("다른 노인의 대화에서는 턴을 시작할 수 없다")
    void beginTurn_notOwner_throws() {
        // given
        given(elderlyRepository.findByMemberIdAndDeletedFalse(MEMBER_ID)).willReturn(Optional.of(elderly(ELDERLY_ID)));
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID))
                .willReturn(Optional.of(conversation(elderly(77L))));

        // when & then
        assertThatThrownBy(() -> recorder.beginTurn(MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("없는 대화에서는 턴을 시작할 수 없다")
    void beginTurn_notFound_throws() {
        // given
        given(elderlyRepository.findByMemberIdAndDeletedFalse(MEMBER_ID)).willReturn(Optional.of(elderly(ELDERLY_ID)));
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> recorder.beginTurn(MEMBER_ID, CONVERSATION_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Test
    @DisplayName("노인 발화를 turnId와 함께 저장하고 RESPONDING 상태가 된다")
    void recordElderlyUtterance() {
        // given
        Conversation conversation = conversation(elderly(ELDERLY_ID));
        conversation.startTurn();
        given(conversationRepository.findById(CONVERSATION_ID)).willReturn(Optional.of(conversation));

        // when
        recorder.recordElderlyUtterance(CONVERSATION_ID, TURN_ID, "밥 먹었어");

        // then
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getSender()).isEqualTo(MessageSender.ELDERLY);
        assertThat(captor.getValue().getContent()).isEqualTo("밥 먹었어");
        assertThat(captor.getValue().getTurnId()).isEqualTo(TURN_ID);
        assertThat(conversation.getState()).isEqualTo(ConversationState.RESPONDING);
    }

    @Test
    @DisplayName("AI 답변을 저장하고 다음 발화를 기다리는 WAITING 상태로 돌아간다")
    void recordAiReply() {
        // given
        Conversation conversation = conversation(elderly(ELDERLY_ID));
        conversation.startTurn();
        given(conversationRepository.findById(CONVERSATION_ID)).willReturn(Optional.of(conversation));

        // when
        recorder.recordAiReply(CONVERSATION_ID, TURN_ID, "잘하셨어요");

        // then
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getSender()).isEqualTo(MessageSender.AI);
        assertThat(captor.getValue().getTurnId()).isEqualTo(TURN_ID);
        assertThat(conversation.getState()).isEqualTo(ConversationState.WAITING);
    }

    @Test
    @DisplayName("턴 도중 대화가 종료되었으면 답변은 저장하되 종료 상태를 유지한다")
    void recordAiReply_afterFinished() {
        // given
        Conversation conversation = conversation(elderly(ELDERLY_ID));
        conversation.startTurn();
        conversation.finish(LocalDateTime.now());
        given(conversationRepository.findById(CONVERSATION_ID)).willReturn(Optional.of(conversation));

        // when
        recorder.recordAiReply(CONVERSATION_ID, TURN_ID, "잘하셨어요");

        // then
        verify(messageRepository).save(any(Message.class));
        assertThat(conversation.getState()).isEqualTo(ConversationState.FINISHED);
    }

    @Test
    @DisplayName("답변 없이 턴을 끝내면 메시지 없이 WAITING 상태로 돌아간다")
    void endTurnWithoutReply() {
        // given
        Conversation conversation = conversation(elderly(ELDERLY_ID));
        conversation.startTurn();
        given(conversationRepository.findById(CONVERSATION_ID)).willReturn(Optional.of(conversation));

        // when
        recorder.endTurnWithoutReply(CONVERSATION_ID);

        // then
        verify(messageRepository, never()).save(any());
        assertThat(conversation.getState()).isEqualTo(ConversationState.WAITING);
    }
}
