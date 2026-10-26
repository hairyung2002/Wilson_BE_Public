package com.piuda.wilson.infrastructure.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FCM 설정 (api-spec 17장). 서비스 계정 JSON은 K8s Secret을 파일로 마운트해 경로만 넘긴다.
 *
 * @param enabled         끄면 푸시를 보내지 않고 기록만 남긴다 (로컬)
 * @param projectId       Firebase 프로젝트 ID
 * @param credentialsPath 서비스 계정 JSON 파일 경로
 */
@ConfigurationProperties("wilson.push.fcm")
public record FcmProperties(boolean enabled, String projectId, String credentialsPath) {
}
