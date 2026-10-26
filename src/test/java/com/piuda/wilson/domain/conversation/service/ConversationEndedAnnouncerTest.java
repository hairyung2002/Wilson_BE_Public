package com.piuda.wilson.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import com.piuda.wilson.domain.conversation.repository.MessageRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.infrastructure.ai.ConversationEndedEvent;
import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConversationEndedAnnouncerTest {

    private static final Long CONVERSATION_ID = 100L;
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 10, 22, 12, 0);
    private static final LocalDateTime ENDED_AT = LocalDateTime.of(2026, 10, 22, 12, 10);

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ConversationEndedAnnouncer announcer;

    private Conversation finishedConversation() {
        Elderly elderly = Elderly.builder().name("김순자").build();
        Conversation conversation = Conversation.builder().elderly(elderly).channel(ConversationChannel.VOICE)
                .type(ConversationType.MEAL).startedAt(STARTED_AT).build();
        ReflectionTestUtils.setField(conversation, "id", CONVERSATION_ID);
        conversation.finish(ENDED_AT);
        return conversation;
    }

    private static Message message(String turnId, MessageSender sender, String content) {
        return Message.builder().turnId(turnId).sender(sender).content(content).build();
    }

    @Test
    @DisplayName("끝난 대화의 턴을 오래된 순으로 묶어 대화 종료 이벤트를 낸다 (안부 턴은 발화 빈 값, 답 못 받은 발화는 답변 빈 값)")
    void announce() {
        // given
        Conversation conversation = finishedConversation();
        given(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(CONVERSATION_ID)).willReturn(List.of(
                message("turn-g", MessageSender.AI, "할머니, 오늘 점심은 뭐 드셨어요?"),
                message("turn-1", MessageSender.ELDERLY, "된장찌개"),
                message("turn-1", MessageSender.AI, "맛있으셨겠어요"),
                message("turn-2", MessageSender.ELDERLY, "근데 말이야"),
                message(null, MessageSender.AI, "턴 식별자 없는 옛 메시지")));

        // when
        announcer.announce(conversation);

        // then
        ArgumentCaptor<ConversationEndedEvent> event = ArgumentCaptor.forClass(ConversationEndedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().subjectId()).isEqualTo(conversation.getElderly().getSubjectId().toString());
        assertThat(event.getValue().conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(event.getValue().traceId()).hasSize(36);
        assertThat(event.getValue().startedAt()).isEqualTo(STARTED_AT.atZone(ZoneId.systemDefault()).toInstant());
        assertThat(event.getValue().endedAt()).isEqualTo(ENDED_AT.atZone(ZoneId.systemDefault()).toInstant());
        assertThat(event.getValue().turns()).containsExactly(
                new DialogueHistoryTurn("turn-g", "", "할머니, 오늘 점심은 뭐 드셨어요?"),
                new DialogueHistoryTurn("turn-1", "된장찌개", "맛있으셨겠어요"),
                new DialogueHistoryTurn("turn-2", "근데 말이야", ""));
    }

    @Test
    @DisplayName("한 마디도 오가지 않은 대화는 알리지 않는다")
    void announce_noTurns() {
        // given
        given(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(CONVERSATION_ID)).willReturn(List.of());

        // when
        announcer.announce(finishedConversation());

        // then
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
