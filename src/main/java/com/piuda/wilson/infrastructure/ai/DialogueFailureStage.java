package com.piuda.wilson.infrastructure.ai;

public enum DialogueFailureStage {
    /** AI 서버가 STT 단계에서 실패를 알림. */
    STT,
    /** AI 서버가 답변 생성 단계에서 실패를 알림. */
    LLM,
    /** AI 서버가 음성 변환 단계에서 실패를 알림. */
    TTS,
    /** AI 서버가 단계를 지정하지 않고 실패를 알림. */
    UNSPECIFIED,
    /** AI 서버에 닿지 못함 (연결 실패·타임아웃·Circuit Open·비정상 스트림). */
    UNAVAILABLE
}
