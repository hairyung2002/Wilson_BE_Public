package com.piuda.wilson.domain.conversation.entity;

/** 선제 대화 진행 상태 (api-spec 6-2). */
public enum ProactiveStatus {
    /** 노인 기기로 푸시를 보냄 */
    SENT,
    /** 10분 동안 응답이 없어 한 번 더 보냄 */
    RETRIED,
    /** 노인이 그 대화에서 말함 */
    RESPONDED,
    /** 다시 10분 동안 응답이 없어 보호자에게 알림 */
    MISSED,
    /** 노인 기기(푸시 토큰)가 없어 보내지 못함 */
    UNDELIVERABLE
}
