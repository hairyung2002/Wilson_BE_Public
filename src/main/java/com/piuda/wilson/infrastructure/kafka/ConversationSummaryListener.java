package com.piuda.wilson.infrastructure.kafka;

import com.piuda.wilson.domain.conversation.dto.ConversationSummaryMessage;
import com.piuda.wilson.domain.conversation.service.ConversationSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * AI 워커 → BE 보호자용 대화 요약 Kafka 수신기 (결정 J2, docs/ai-integration-contract.md 4장).
 *
 * <p>형식이 잘못된 메시지는 {@link KafkaJsonReader}가 걸러 버린다. DB 장애처럼 다시 시도하면 될 수 있는
 * 실패만 예외로 던져 {@code KafkaConfig}의 재시도에 맡긴다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationSummaryListener {

    static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final KafkaJsonReader reader;
    private final ConversationSummaryService conversationSummaryService;

    @KafkaListener(topics = "${wilson.kafka.conversation-summaries.topic}",
            autoStartup = "${wilson.kafka.conversation-summaries.auto-startup}")
    public void onMessage(ConsumerRecord<String, String> record) {
        reader.read(record, ConversationSummaryMessage.class)
                .filter(message -> isSupported(message, record))
                .ifPresent(conversationSummaryService::record);
    }

    private static boolean isSupported(ConversationSummaryMessage message, ConsumerRecord<String, String> record) {
        if (message.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            log.warn("Unsupported conversation summary schema discarded: partition={}, offset={}, schemaVersion={}",
                    record.partition(), record.offset(), message.schemaVersion());
            return false;
        }
        return true;
    }
}
