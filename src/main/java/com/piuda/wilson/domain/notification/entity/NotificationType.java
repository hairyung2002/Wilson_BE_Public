package com.piuda.wilson.domain.notification.entity;

/** 보호자 알림 종류 (api-spec 14-1, 결정 E2). */
public enum NotificationType {
    /** 대화 패턴 변화 (분석 판정 위험도 상승) */
    PATTERN_CHANGE,
    /** 선제 대화에 노인이 응답하지 않음 */
    PROACTIVE_NO_RESPONSE,
    /** 응급 — 연결된 보호자 전원, 즉시, 끌 수 없음 */
    EMERGENCY,
    /** 하루 대화 요약 (매일 20:00) */
    DAILY_SUMMARY
}
