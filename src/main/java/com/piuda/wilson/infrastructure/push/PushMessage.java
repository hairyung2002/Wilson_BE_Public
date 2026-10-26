package com.piuda.wilson.infrastructure.push;

import java.util.Map;

/**
 * 푸시 한 건. title이 없으면 화면 알림 없이 데이터만 보낸다 (선제 대화로 노인 앱을 깨울 때).
 * 대화 원문·실명 같은 개인정보는 넣지 않는다.
 */
public record PushMessage(String token, String title, String body, Map<String, String> data) {
}
