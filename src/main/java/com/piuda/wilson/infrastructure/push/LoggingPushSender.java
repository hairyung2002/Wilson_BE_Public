package com.piuda.wilson.infrastructure.push;

import lombok.extern.slf4j.Slf4j;

/** FCM 설정이 없는 로컬 환경용. 보내지 않고 기록만 남긴다 (토큰·본문은 남기지 않는다). */
@Slf4j
public class LoggingPushSender implements PushSender {

    @Override
    public PushResult send(PushMessage message) {
        log.info("Push skipped (FCM disabled): type={}", message.data() == null ? null : message.data().get("type"));
        return PushResult.SENT;
    }
}
