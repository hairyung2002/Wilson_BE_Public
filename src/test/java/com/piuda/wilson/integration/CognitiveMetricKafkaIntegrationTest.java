package com.piuda.wilson.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.piuda.wilson.domain.analysis.entity.CognitiveMetric;
import com.piuda.wilson.domain.analysis.repository.CognitiveMetricRepository;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 내장 Kafka 브로커로 AI 서버의 발행을 흉내내 BE Consumer → DB 저장까지 검증한다.
 *
 * <p>끝나면 컨텍스트를 닫으므로(@DirtiesContext) 별도 H2 DB를 쓴다. 공유 DB를 쓰면 종료 시 create-drop이
 * 다른 테스트 컨텍스트가 쓰는 테이블까지 지운다.</p>
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "wilson.kafka.cognitive-metrics.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:wilson-kafka-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@EmbeddedKafka(partitions = 1, topics = CognitiveMetricKafkaIntegrationTest.TOPIC)
@DirtiesContext
class CognitiveMetricKafkaIntegrationTest {

    static final String TOPIC = "wilson.cognitive-metrics.v1";

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private CognitiveMetricRepository cognitiveMetricRepository;

    private KafkaTemplate<String, String> kafkaTemplate;

    /** AI 서버처럼 JSON 문자열로 보낸다. 앱의 KafkaTemplate은 BE → AI Protobuf 전용이다. */
    @BeforeEach
    void setUp() {
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
    }

    private static String message(UUID turnId) {
        return """
                {"schema_version":1,"turn_id":"%s","subject_id":"0ef3e83e-9b1d-4572-9b78-4221ccaf936e",
                 "session_id":"15","analyzed_at":"2026-10-20T10:15:30+09:00","analyzable":true,
                 "metrics":{"morpheme_token_count":42,"filler_ratio":0.12,"jitter":0.011}}
                """.formatted(turnId);
    }

    @Test
    @DisplayName("깨진 메시지는 건너뛰고, 같은 턴이 두 번 와도 한 번만 저장한다")
    void consumeCognitiveMetrics() {
        // given
        UUID turnId = UUID.randomUUID();
        UUID lastTurnId = UUID.randomUUID();

        // when: 한 파티션이라 보낸 순서대로 처리된다
        kafkaTemplate.send(TOPIC, "broken", "{not-json");
        kafkaTemplate.send(TOPIC, turnId.toString(), message(turnId));
        kafkaTemplate.send(TOPIC, turnId.toString(), message(turnId));
        kafkaTemplate.send(TOPIC, lastTurnId.toString(), message(lastTurnId));

        // then
        await().atMost(Duration.ofSeconds(20))
                .until(() -> cognitiveMetricRepository.existsByTurnId(lastTurnId.toString()));
        assertThat(cognitiveMetricRepository.findAll())
                .filteredOn(metric -> metric.getTurnId().equals(turnId.toString()))
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.getMetrics().morphemeTokenCount()).isEqualTo(42);
                    assertThat(metric.getMetrics().utteranceCount()).as("보내지 않은 지표는 비어 있다").isNull();
                });
        assertThat(cognitiveMetricRepository.findAll()).extracting(CognitiveMetric::getTurnId)
                .doesNotContain("broken");
    }
}
