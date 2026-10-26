package com.piuda.wilson.infrastructure.ai;

/**
 * BE가 정한 문장을 AI 목소리로 읽어 달라는 요청 (선제 안부, 결정 J4). AI는 문장을 바꾸지 않는다.
 * subjectId는 노인 PK가 아닌 가명 식별자다.
 */
public record SpeechRequest(String traceId, String subjectId, Long conversationId, String turnId, String text) {
}
