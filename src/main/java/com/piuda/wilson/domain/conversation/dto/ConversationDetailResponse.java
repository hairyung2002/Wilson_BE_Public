package com.piuda.wilson.domain.conversation.dto;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationOrigin;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 대화 상세 (api-spec 6-10). 보호자는 기본적으로 요약만 보고, 노인의 원문 공유 동의가 있을 때만
 * messages가 채워진다 (transcriptVisible). summary는 AI 워커가 요약을 보내기 전까지 null이다 (결정 J2).
 */
public record ConversationDetailResponse(Long conversationId, ConversationType conversationType,
                                         ConversationOrigin origin, ConversationState state, LocalDateTime startedAt,
                                         LocalDateTime endedAt, String summary, boolean transcriptVisible,
                                         List<MessageResponse> messages) {

    public record MessageResponse(MessageSender role, String text, LocalDateTime createdAt) {

        public static MessageResponse from(Message message) {
            return new MessageResponse(message.getSender(), message.getContent(), message.getCreatedAt());
        }
    }

    public static ConversationDetailResponse withTranscript(Conversation conversation, List<Message> messages) {
        return of(conversation, true, messages.stream().map(MessageResponse::from).toList());
    }

    public static ConversationDetailResponse withoutTranscript(Conversation conversation) {
        return of(conversation, false, List.of());
    }

    private static ConversationDetailResponse of(Conversation conversation, boolean transcriptVisible,
                                                 List<MessageResponse> messages) {
        return new ConversationDetailResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getOrigin(),
                conversation.getState(),
                conversation.getStartedAt(),
                conversation.getEndedAt(),
                conversation.getSummary(),
                transcriptVisible,
                messages
        );
    }
}
