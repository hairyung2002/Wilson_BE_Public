package com.piuda.wilson.infrastructure.ai;

import java.util.List;

/**
 * subjectId는 노인 PK가 아닌 가명 식별자다. AI 서버에는 이 값만 노인 식별자로 넘긴다.
 * history는 같은 대화의 앞선 턴들(오래된 순)이다. AI 서버는 무상태라 맥락은 BE가 들고 있다가 매번 보낸다.
 * addressTerm은 답변에서 어르신을 부르는 말(할머니·할아버지·어르신)이다. 실명은 보내지 않는다 (결정 J7).
 */
public record DialogueRequest(byte[] audio, String traceId, String subjectId, Long conversationId,
                              String turnId, String audioFormat, List<DialogueHistoryTurn> history,
                              String addressTerm) {
}
