package com.piuda.wilson.infrastructure.push;

/** 모바일 푸시 발송 어댑터. 예외를 던지지 않고 결과로 알려 준다. */
public interface PushSender {

    PushResult send(PushMessage message);
}
