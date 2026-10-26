package com.piuda.wilson.domain.conversation.service;

/**
 * 선제 대화의 첫 안부 턴 (결정 J4). 노인 발화 없이 AI가 text를 읽는다.
 * subjectId는 AI 서버로 보내는 노인 가명 식별자다.
 */
public record GreetingTurn(String subjectId, Long conversationId, String turnId, String traceId, String text) {
}
