package com.piuda.wilson.domain.conversation.dto;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationState;

public record StartConversationResponse(Long conversationId, ConversationState state) {

    public static StartConversationResponse from(Conversation conversation) {
        return new StartConversationResponse(conversation.getId(), conversation.getState());
    }
}
