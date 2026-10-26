package com.piuda.wilson.domain.conversation.service;

import com.piuda.wilson.infrastructure.ai.AiEventPublisher;
import com.piuda.wilson.infrastructure.ai.ConversationEndedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 대화 종료가 커밋된 뒤에 AI로 발행한다 (결정 J2). 트랜잭션 밖에서 발행되면(fallbackExecution) 바로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class ConversationEndedRelay {

    private final AiEventPublisher aiEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEnded(ConversationEndedEvent event) {
        aiEventPublisher.publishConversationEnded(event);
    }
}
