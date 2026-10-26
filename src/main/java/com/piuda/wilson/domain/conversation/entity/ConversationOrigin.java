package com.piuda.wilson.domain.conversation.entity;

/** 대화를 누가 시작했는지 (api-spec 6-9). */
public enum ConversationOrigin {
    /** 노인이 직접 시작 */
    USER,
    /** 일정에 따라 AI가 먼저 말을 건 선제 대화 (6-2) */
    PROACTIVE
}
