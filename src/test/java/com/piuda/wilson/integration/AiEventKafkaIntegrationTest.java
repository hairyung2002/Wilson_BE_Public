package com.piuda.wilson.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.infrastructure.ai.AiEventPublisher;
import com.piuda.wilson.infrastructure.ai.SubjectErasureEvent;
import com.piuda.wilson.infrastructure.ai.TurnAnalysisEvent;
import com.piuda.wilson.infrastructure.kafka.events.v1.TurnAnalysisRequested;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 내장 Kafka 브로커로 BE ↔ AI 이벤트 계약을 검증한다 (docs/ai-integration-contract.md).
 *
 * <p>BE → AI 토픽은 앱이 만든다(보관 기간·메시지 크기 설정 포함). 브로커 기본 한도(1MB)를 넘는 음성도 지나가야 한다.
 * 끝나면 컨텍스트를 닫으므로(@DirtiesContext) 별도 H2 DB를 쓴다.</p>
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=true",
        "wilson.kafka.ai-events.enabled=true",
        "wilson.kafka.ai-events.partitions=1",
        "wilson.kafka.conversation-summaries.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:wilson-ai-event-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@EmbeddedKafka(partitions = 1, topics = AiEventKafkaIntegrationTest.SUMMARY_TOPIC)
@DirtiesContext
class AiEventKafkaIntegrationTest {

    static final String SUMMARY_TOPIC = "wilson.conversation-summaries.v1";
    private static final String TURN_ANALYSIS_TOPIC = "wilson.turn-analysis.v1";

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private AiEventPublisher aiEventPublisher;

    @Autowired
    private ElderlyRepository elderlyRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    private Consumer<String, byte[]> consumer(String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "ai-worker-" + UUID.randomUUID(), true);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, byte[]> consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new ByteArrayDeserializer()).createConsumer();
        consumer.subscribe(java.util.List.of(topic));
        return consumer;
    }

    @Test
    @DisplayName("턴 분석 요청을 Protobuf로 보내고, 1MB를 넘는 음성도 AI 워커가 그대로 받는다 (J1·J8)")
    void turnAnalysis() throws Exception {
        // given
        byte[] audio = new byte[3 * 1024 * 1024];
        audio[audio.length - 1] = 7;
        String turnId = UUID.randomUUID().toString();

        // when
        aiEventPublisher.publishTurnAnalysis(new TurnAnalysisEvent("trace-1", turnId, UUID.randomUUID().toString(),
                15L, audio, "wav", "밥 먹었어", "잘하셨어요", Instant.now()));

        // then
        try (Consumer<String, byte[]> consumer = consumer(TURN_ANALYSIS_TOPIC)) {
            ConsumerRecord<String, byte[]> record =
                    KafkaTestUtils.getSingleRecord(consumer, TURN_ANALYSIS_TOPIC, Duration.ofSeconds(20));
            TurnAnalysisRequested message = TurnAnalysisRequested.parseFrom(record.value());
            assertThat(record.key()).isEqualTo(turnId);
            assertThat(message.getAudioPayload().size()).isEqualTo(audio.length);
            assertThat(message.getAudioPayload().byteAt(audio.length - 1)).isEqualTo((byte) 7);
            assertThat(message.getSttText()).isEqualTo("밥 먹었어");
        }
    }

    @Test
    @DisplayName("파기 요청은 브로커가 받았는지 확인한 뒤 성공으로 본다 (J3)")
    void subjectErasure() {
        // when & then
        assertThat(aiEventPublisher.publishSubjectErasure(
                new SubjectErasureEvent("trace-2", UUID.randomUUID().toString(), Instant.now()))).isTrue();
    }

    @Test
    @DisplayName("AI 워커가 보낸 요약을 그 대화에 저장한다 (J2)")
    void conversationSummary() {
        // given
        Elderly elderly = elderlyRepository.save(Elderly.builder().name("김순자").build());
        Conversation conversation = conversationRepository.save(Conversation.builder().elderly(elderly)
                .channel(ConversationChannel.VOICE).type(ConversationType.MEAL).startedAt(LocalDateTime.now()).build());
        String summary = "점심으로 된장찌개를 드셨어요.";
        KafkaTemplate<String, String> aiWorker = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));

        // when
        aiWorker.send(SUMMARY_TOPIC, String.valueOf(conversation.getId()), """
                {"schema_version":1,"session_id":"%d","target_user_id":"%s",
                 "summary":"%s","generated_at":"2026-10-22T03:12:45Z"}
                """.formatted(conversation.getId(), elderly.getSubjectId(), summary));

        // then
        await().atMost(Duration.ofSeconds(20)).until(() ->
                summary.equals(conversationRepository.findById(conversation.getId()).orElseThrow().getSummary()));
    }
}
