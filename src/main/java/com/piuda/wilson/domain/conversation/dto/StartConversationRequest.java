package com.piuda.wilson.domain.conversation.dto;

import com.piuda.wilson.domain.conversation.entity.ConversationType;
import jakarta.validation.constraints.NotNull;

public record StartConversationRequest(

        @NotNull(message = "대화 유형은 필수입니다.")
        ConversationType conversationType
) {
}
