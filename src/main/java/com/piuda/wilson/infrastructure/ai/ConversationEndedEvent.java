package com.piuda.wilson.infrastructure.ai;

import java.time.Instant;
import java.util.List;

/**
 * 끝난 대화 한 건 (결정 J2). AI 워커가 RAG에 저장하고 보호자용 요약을 만든다.
 *
 * @param turns 오래된 순. 선제 안부 턴은 sttText가 빈 값
 */
public record ConversationEndedEvent(String traceId, String subjectId, Long conversationId, Instant startedAt,
                                     Instant endedAt, List<DialogueHistoryTurn> turns) {
}
