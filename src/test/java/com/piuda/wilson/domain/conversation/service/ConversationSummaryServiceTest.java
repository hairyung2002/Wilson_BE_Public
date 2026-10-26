package com.piuda.wilson.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.piuda.wilson.domain.conversation.dto.ConversationSummaryMessage;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceTest {

    private static final Long CONVERSATION_ID = 15L;
    private static final String SUMMARY = "점심으로 된장찌개를 드셨고, 오후에 산책을 다녀오실 계획을 이야기하셨어요.";

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private ConversationSummaryService conversationSummaryService;

    private final Elderly elderly = Elderly.builder().name("김순자").build();

    private Conversation givenConversation() {
        Conversation conversation = Conversation.builder().elderly(elderly).channel(ConversationChannel.VOICE)
                .type(ConversationType.MEAL).startedAt(LocalDateTime.of(2026, 10, 22, 12, 0)).build();
        ReflectionTestUtils.setField(conversation, "id", CONVERSATION_ID);
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.of(conversation));
        return conversation;
    }

    private static ConversationSummaryMessage message(UUID subjectId, String summary) {
        return new ConversationSummaryMessage(1, String.valueOf(CONVERSATION_ID), subjectId, summary,
                OffsetDateTime.parse("2026-10-22T03:12:45Z"));
    }

    @Test
    @DisplayName("그 노인의 대화에 온 요약을 저장하고, 다시 오면 덮어쓴다")
    void record() {
        // given
        Conversation conversation = givenConversation();

        // when
        conversationSummaryService.record(message(elderly.getSubjectId(), "  처음 요약이에요.  "));
        conversationSummaryService.record(message(elderly.getSubjectId(), SUMMARY));

        // then
        assertThat(conversation.getSummary()).isEqualTo(SUMMARY);
    }

    @Test
    @DisplayName("가명 식별자가 지금 노인과 다르면 버린다 (삭제 뒤 늦게 도착한 요약)")
    void record_subjectMismatch() {
        // given
        Conversation conversation = givenConversation();

        // when
        conversationSummaryService.record(message(UUID.randomUUID(), SUMMARY));

        // then
        assertThat(conversation.getSummary()).isNull();
    }

    @Test
    @DisplayName("없거나 삭제된 대화의 요약은 버린다")
    void record_conversationMissing() {
        // given
        given(conversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID)).willReturn(Optional.empty());

        // when & then (예외 없이 끝난다)
        conversationSummaryService.record(message(elderly.getSubjectId(), SUMMARY));
    }

    @Test
    @DisplayName("의료 판단처럼 읽히는 표현(치매·진단·질환)이 있으면 보호자에게 보이지 않도록 버린다 (12-4)")
    void record_medicalWording() {
        // given
        Conversation conversation = givenConversation();

        // when
        conversationSummaryService.record(message(elderly.getSubjectId(), "치매 초기 증상이 의심되는 대화였어요."));

        // then
        assertThat(conversation.getSummary()).isNull();
    }
}
