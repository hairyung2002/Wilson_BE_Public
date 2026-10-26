package com.piuda.wilson.infrastructure.push;

public enum PushResult {
    SENT,
    /** 앱 삭제 등으로 더 이상 쓸 수 없는 토큰. 저장된 토큰을 지운다 */
    INVALID_TOKEN,
    /** FCM 장애·인증 오류 등 다시 보내면 될 수 있는 실패 */
    FAILED
}
