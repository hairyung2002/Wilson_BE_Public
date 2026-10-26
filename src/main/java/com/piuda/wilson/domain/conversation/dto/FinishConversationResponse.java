package com.piuda.wilson.domain.conversation.dto;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import java.time.LocalDateTime;

public record FinishConversationResponse(Long conversationId, ConversationState state, LocalDateTime endedAt) {

    public static FinishConversationResponse from(Conversation conversation) {
        return new FinishConversationResponse(conversation.getId(), conversation.getState(), conversation.getEndedAt());
    }
}
