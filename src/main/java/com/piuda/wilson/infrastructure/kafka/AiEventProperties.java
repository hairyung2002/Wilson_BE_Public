package com.piuda.wilson.infrastructure.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BE → AI 이벤트 토픽 (docs/ai-integration-contract.md 0장).
 *
 * @param enabled    끄면 발행하지 않고 기록만 남긴다 (Kafka 없이 띄우는 로컬)
 * @param ackTimeout 파기 요청처럼 브로커 수신을 확인해야 하는 발행에서 기다리는 최대 시간
 * @param partitions 토픽을 만들 때의 파티션 수 (spring.kafka.admin.auto-create가 켜진 경우)
 * @param replicas   토픽을 만들 때의 복제 수
 */
@ConfigurationProperties("wilson.kafka.ai-events")
public record AiEventProperties(boolean enabled, String turnAnalysisTopic, String conversationEndedTopic,
                                String subjectErasureTopic, Duration ackTimeout, int partitions, int replicas) {
}
