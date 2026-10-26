package com.piuda.wilson.infrastructure.ai;

/**
 * BE → AI 비동기 이벤트 발행 포트 (Kafka, docs/ai-integration-contract.md 2·3·5장).
 *
 * <p>무거운 처리(지표 분석·RAG 저장·요약·파기)는 AI 워커가 따로 소비하므로 대화는 기다리지 않는다.
 * 구현체는 예외를 던지지 않는다.</p>
 */
public interface AiEventPublisher {

    /** 턴 분석 요청 (J1). 결과를 기다리지 않고, 실패는 기록만 한다. */
    void publishTurnAnalysis(TurnAnalysisEvent event);

    /** 대화 종료 (J2). 결과를 기다리지 않고, 실패는 기록만 한다. */
    void publishConversationEnded(ConversationEndedEvent event);

    /** 노인 파기 요청 (J3). 브로커가 받았는지 기다리며, 받지 못했으면 false (호출자가 다시 보낸다). */
    boolean publishSubjectErasure(SubjectErasureEvent event);
}
