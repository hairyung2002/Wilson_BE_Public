package com.piuda.wilson.domain.conversation.service;

/**
 * 시작된 음성 턴 한 건. turnId는 AI 서버 요청과 인지 지표에서 같은 값으로 쓰이는 조인 키다.
 * subjectId는 AI 서버로 보내는 노인 가명 식별자, addressTerm은 실명 대신 쓰는 호칭이다.
 */
public record VoiceTurn(String subjectId, Long conversationId, String turnId, String traceId, String addressTerm) {
}
