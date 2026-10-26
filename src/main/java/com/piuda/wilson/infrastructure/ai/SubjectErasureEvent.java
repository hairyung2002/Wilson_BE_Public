package com.piuda.wilson.infrastructure.ai;

import java.time.Instant;

/** 삭제된 노인의 AI 쪽 데이터(RAG 임베딩) 파기 요청 (결정 J3). subjectId는 바꾸기 전의 가명 식별자다. */
public record SubjectErasureEvent(String traceId, String subjectId, Instant requestedAt) {
}
