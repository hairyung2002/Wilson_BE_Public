package com.piuda.wilson.infrastructure.kafka;

import com.piuda.wilson.domain.analysis.dto.CognitiveMetricMessage;
import com.piuda.wilson.domain.analysis.service.CognitiveMetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * AI 서버 → BE 인지지표 Kafka 수신기 (docs/kafka-cognitive-metrics.md).
 *
 * <p>형식이 잘못된 메시지는 {@link KafkaJsonReader}가 걸러 버린다. DB 장애처럼 다시 시도하면 될 수 있는
 * 실패만 예외로 던져 {@code KafkaConfig}의 재시도에 맡긴다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CognitiveMetricListener {

    static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final KafkaJsonReader reader;
    private final CognitiveMetricService cognitiveMetricService;

    @KafkaListener(topics = "${wilson.kafka.cognitive-metrics.topic}",
            autoStartup = "${wilson.kafka.cognitive-metrics.auto-startup}")
    public void onMessage(ConsumerRecord<String, String> record) {
        reader.read(record, CognitiveMetricMessage.class)
                .filter(message -> isSupported(message, record))
                .ifPresent(cognitiveMetricService::record);
    }

    private static boolean isSupported(CognitiveMetricMessage message, ConsumerRecord<String, String> record) {
        if (message.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            log.warn("Unsupported cognitive metric schema discarded: partition={}, offset={}, schemaVersion={}",
                    record.partition(), record.offset(), message.schemaVersion());
            return false;
        }
        return true;
    }
}
