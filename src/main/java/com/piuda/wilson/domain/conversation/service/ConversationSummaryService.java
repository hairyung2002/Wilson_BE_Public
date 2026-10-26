package com.piuda.wilson.domain.conversation.service;

import com.piuda.wilson.domain.conversation.dto.ConversationSummaryMessage;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 워커가 만든 보호자용 대화 요약을 저장한다 (결정 J2, docs/ai-integration-contract.md 4장).
 *
 * <p>요약은 보호자에게 그대로 보이므로 그대로 믿지 않는다. 대화가 없거나 삭제됐거나, 노인이 삭제돼 가명 식별자가
 * 바뀌었으면 버린다. 의료 판단처럼 읽히는 표현이 있으면 보여 주지 않는다 (api-spec 12-4). 같은 대화에 다시 오면
 * 덮어쓰므로 같은 메시지를 두 번 받아도 결과가 같다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private static final List<String> MEDICAL_WORDS = List.of("치매", "진단", "질환");

    private final ConversationRepository conversationRepository;

    @Transactional
    public void record(ConversationSummaryMessage message) {
        Long conversationId = Long.valueOf(message.sessionId());
        conversationRepository.findByIdAndDeletedFalse(conversationId)
                .filter(conversation -> isSameSubject(conversation, message))
                .filter(conversation -> isPresentable(message.summary(), conversationId))
                .ifPresentOrElse(conversation -> {
                    conversation.updateSummary(message.summary().strip());
                    log.info("Conversation summary recorded: conversationId={}", conversationId);
                }, () -> log.info("Conversation summary discarded: conversationId={}", conversationId));
    }

    private static boolean isSameSubject(Conversation conversation, ConversationSummaryMessage message) {
        return conversation.getElderly().getSubjectId().equals(message.targetUserId());
    }

    private static boolean isPresentable(String summary, Long conversationId) {
        boolean medical = MEDICAL_WORDS.stream().anyMatch(summary::contains);
        if (medical) {
            log.warn("Conversation summary with medical wording discarded: conversationId={}", conversationId);
        }
        return !medical;
    }
}
