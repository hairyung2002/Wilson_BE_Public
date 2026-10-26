package com.piuda.wilson.domain.conversation.dto;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationOrigin;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import java.time.LocalDateTime;

/**
 * 대화 목록 항목. origin은 노인이 직접 시작했는지(USER), 선제 대화인지(PROACTIVE).
 * summary는 보호자용 요약이며, 대화가 끝난 뒤 AI 워커가 만들어 보내기 전까지 null이다 (결정 J2).
 */
public record ConversationSummaryResponse(Long conversationId, ConversationType conversationType,
                                          ConversationOrigin origin, ConversationState state,
                                          LocalDateTime startedAt, LocalDateTime endedAt, String summary) {

    public static ConversationSummaryResponse from(Conversation conversation) {
        return new ConversationSummaryResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getOrigin(),
                conversation.getState(),
                conversation.getStartedAt(),
                conversation.getEndedAt(),
                conversation.getSummary()
        );
    }
}
