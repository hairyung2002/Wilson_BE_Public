package com.piuda.wilson.infrastructure.ai;

import java.time.Instant;

/**
 * 노인 발화 한 턴의 분석 요청 (결정 J1). audio는 AI 대화 요청에 보낸 것과 같은 원본이다.
 *
 * @param llmResponseText AI 답변. 답변 생성에 실패했으면 null
 */
public record TurnAnalysisEvent(String traceId, String turnId, String subjectId, Long conversationId, byte[] audio,
                                String audioFormat, String sttText, String llmResponseText, Instant occurredAt) {
}
