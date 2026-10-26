package com.piuda.wilson.domain.notification.entity;

/** 알림의 푸시 발송 상태. 앱 내 알림 목록과 별개다. */
public enum PushStatus {
    /** 커밋 뒤 곧 보낼 예정 */
    QUEUED,
    /** 22:00~07:00이라 07:00에 묶어 보낼 예정 */
    DEFERRED,
    SENT,
    /** 보호자 기기(푸시 토큰)가 없음 */
    NO_DEVICE,
    /** 재시도 후에도 실패 */
    FAILED
}
