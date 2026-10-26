package com.piuda.wilson.infrastructure.kafka;

import com.piuda.wilson.infrastructure.ai.AiEventPublisher;
import com.piuda.wilson.infrastructure.ai.ConversationEndedEvent;
import com.piuda.wilson.infrastructure.ai.SubjectErasureEvent;
import com.piuda.wilson.infrastructure.ai.TurnAnalysisEvent;
import lombok.extern.slf4j.Slf4j;

/** Kafka 없이 띄우는 로컬 환경용. 보내지 않고 식별자만 기록한다 (음성·원문은 남기지 않는다). */
@Slf4j
public class LoggingAiEventPublisher implements AiEventPublisher {

    @Override
    public void publishTurnAnalysis(TurnAnalysisEvent event) {
        log.info("AI event skipped (disabled): turn-analysis, turnId={}", event.turnId());
    }

    @Override
    public void publishConversationEnded(ConversationEndedEvent event) {
        log.info("AI event skipped (disabled): conversation-ended, conversationId={}", event.conversationId());
    }

    /** 보낼 곳이 없으므로 처리된 것으로 본다 (재시도가 쌓이지 않게). */
    @Override
    public boolean publishSubjectErasure(SubjectErasureEvent event) {
        log.info("AI event skipped (disabled): subject-erasure");
        return true;
    }
}
