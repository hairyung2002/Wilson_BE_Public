package com.piuda.wilson.domain.conversation.service;

import com.piuda.wilson.domain.conversation.entity.Conversation;
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
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 음성 턴의 상태 변경과 메시지 저장을 담당한다.
 *
 * <p>AI 응답 콜백은 gRPC 스레드에서 들어오므로, 각 단계를 짧은 트랜잭션으로 분리한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationTurnRecorder {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ElderlyRepository elderlyRepository;
    private final ProactiveTriggerRepository proactiveTriggerRepository;

    @Transactional
    public VoiceTurn beginTurn(Long memberId, Long conversationId) {
        Elderly elderly = elderlyRepository.findByMemberIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.ELDERLY_NOT_FOUND));
        Conversation conversation = conversationRepository.findByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONVERSATION_NOT_FOUND));
        validateTurnStartable(conversation, elderly);

        conversation.startTurn();
        return new VoiceTurn(elderly.getSubjectId().toString(), conversationId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), elderly.addressTerm());
    }

    /**
     * 선제 대화의 첫 안부를 AI 발화로 남기고 읽기 시작한다 (결정 J4). 내 선제 대화에서, 아직 아무 말도
     * 오가지 않았을 때만 할 수 있다. 음성 합성이 실패해도 문장은 앱이 대신 읽으므로 먼저 남긴다.
     */
    @Transactional
    public GreetingTurn beginGreeting(Long memberId, Long conversationId) {
        Elderly elderly = elderlyRepository.findByMemberIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.ELDERLY_NOT_FOUND));
        Conversation conversation = conversationRepository.findByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONVERSATION_NOT_FOUND));
        validateTurnStartable(conversation, elderly);
        if (!conversation.isProactive() || messageRepository.existsByConversationId(conversationId)) {
            throw new CustomException(ErrorCode.VOICE_PROTOCOL_VIOLATION);
        }
        String greeting = proactiveTriggerRepository.findByConversationId(conversationId)
                .map(ProactiveTrigger::getGreeting)
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_PROTOCOL_VIOLATION));

        String turnId = UUID.randomUUID().toString();
        saveMessage(conversation, MessageSender.AI, greeting, turnId);
        conversation.startTurn();
        conversation.startResponding();
        return new GreetingTurn(elderly.getSubjectId().toString(), conversationId, turnId,
                UUID.randomUUID().toString(), greeting);
    }

    @Transactional
    public void recordElderlyUtterance(Long conversationId, String turnId, String sttText) {
        withConversation(conversationId, conversation -> {
            saveMessage(conversation, MessageSender.ELDERLY, sttText, turnId);
            conversation.startResponding();
        });
    }

    @Transactional
    public void recordAiReply(Long conversationId, String turnId, String fullLlmResponseText) {
        withConversation(conversationId, conversation -> {
            saveMessage(conversation, MessageSender.AI, fullLlmResponseText, turnId);
            conversation.endTurn();
        });
    }

    @Transactional
    public void endTurnWithoutReply(Long conversationId) {
        withConversation(conversationId, Conversation::endTurn);
    }

    private void validateTurnStartable(Conversation conversation, Elderly elderly) {
        if (!conversation.isOwnedBy(elderly.getId())) {
            throw new CustomException(ErrorCode.NO_PERMISSION);
        }
        if (conversation.isFinished()) {
            throw new CustomException(ErrorCode.CONVERSATION_ALREADY_FINISHED);
        }
        if (conversation.isTurnInProgress()) {
            throw new CustomException(ErrorCode.CONVERSATION_TURN_IN_PROGRESS);
        }
    }

    private void withConversation(Long conversationId, Consumer<Conversation> action) {
        conversationRepository.findById(conversationId).ifPresentOrElse(action,
                () -> log.warn("Conversation vanished during turn: conversationId={}", conversationId));
    }

    private void saveMessage(Conversation conversation, MessageSender sender, String content, String turnId) {
        messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(sender)
                .content(content)
                .turnId(turnId)
                .build());
    }
}
