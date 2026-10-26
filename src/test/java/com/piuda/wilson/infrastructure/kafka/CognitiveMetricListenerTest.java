package com.piuda.wilson.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.analysis.dto.CognitiveMetricMessage;
import com.piuda.wilson.domain.analysis.service.CognitiveMetricService;
import jakarta.validation.Validation;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class CognitiveMetricListenerTest {

    private static final String VALID = """
            {
              "schema_version": 1,
              "turn_id": "ee84a22f-dba5-42ba-a6af-7416d2276dc3",
              "subject_id": "0ef3e83e-9b1d-4572-9b78-4221ccaf936e",
              "session_id": "15",
              "trace_id": "9ab6fd3e-b171-4786-be68-71041c4131ef",
              "analyzed_at": "2026-10-20T10:15:30.123+09:00",
              "analyzable": true,
              "metrics": {
                "morpheme_token_count": 42,
                "utterance_count": 3,
                "filler_ratio": 0.12,
                "empty_speech_ratio": 0.05,
                "content_info_unit_ratio": 0.4,
                "words_per_utterance": 14.0,
                "subsequent_onset_latency_ms": 820.0,
                "silent_pause_ratio": 0.18,
                "jitter": 0.011,
                "shimmer": 0.042,
                "cpp": 12.3
              },
              "neural": {"model": "tbd"},
              "emotion": null,
              "added_later_by_ai": "ignored"
            }
            """;

    private final CognitiveMetricService cognitiveMetricService = mock(CognitiveMetricService.class);
    private final CognitiveMetricListener listener = new CognitiveMetricListener(new KafkaJsonReader(
            JsonMapper.builder().build(), Validation.buildDefaultValidatorFactory().getValidator()),
            cognitiveMetricService);

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("wilson.cognitive-metrics.v1", 0, 7L, "turn-key", value);
    }

    @Test
    @DisplayName("계약대로 온 메시지를 읽어 저장을 요청한다 (모르는 필드는 무시)")
    void onMessage_valid() {
        // when
        listener.onMessage(record(VALID));

        // then
        ArgumentCaptor<CognitiveMetricMessage> captor = ArgumentCaptor.forClass(CognitiveMetricMessage.class);
        verify(cognitiveMetricService).record(captor.capture());
        CognitiveMetricMessage message = captor.getValue();
        assertThat(message.turnId()).isEqualTo(UUID.fromString("ee84a22f-dba5-42ba-a6af-7416d2276dc3"));
        assertThat(message.metrics().subsequentOnsetLatencyMs()).isEqualTo(820.0);
        assertThat(message.neural().toString()).isEqualTo("{\"model\":\"tbd\"}");
    }

    @Test
    @DisplayName("분석 불가 턴은 metrics 없이 저장을 요청한다")
    void onMessage_notAnalyzable() {
        // when
        listener.onMessage(record(VALID.replace("\"analyzable\": true", "\"analyzable\": false")
                .replaceAll("(?s)\"metrics\": \\{.*?},", "\"metrics\": null,")));

        // then
        verify(cognitiveMetricService).record(any());
    }

    @Test
    @DisplayName("JSON이 깨진 메시지는 재시도하지 않고 버린다 (같은 메시지가 무한 재처리되는 것 방지)")
    void onMessage_malformedJson() {
        // when
        listener.onMessage(record("{not-json"));

        // then
        verify(cognitiveMetricService, never()).record(any());
    }

    @Test
    @DisplayName("turn_id가 UUID가 아니면 버린다")
    void onMessage_invalidTurnId() {
        // when
        listener.onMessage(record(VALID.replace("ee84a22f-dba5-42ba-a6af-7416d2276dc3", "turn-1")));

        // then
        verify(cognitiveMetricService, never()).record(any());
    }

    @Test
    @DisplayName("필수 필드가 빠지면 버린다")
    void onMessage_missingRequired() {
        // when
        listener.onMessage(record(VALID.replace("\"subject_id\": \"0ef3e83e-9b1d-4572-9b78-4221ccaf936e\",", "")));

        // then
        verify(cognitiveMetricService, never()).record(any());
    }

    @Test
    @DisplayName("analyzable=true인데 metrics가 없으면 버린다")
    void onMessage_analyzableWithoutMetrics() {
        // when
        listener.onMessage(record(VALID.replaceAll("(?s)\"metrics\": \\{.*?},", "\"metrics\": null,")));

        // then
        verify(cognitiveMetricService, never()).record(any());
    }

    @Test
    @DisplayName("음수 지표는 버린다")
    void onMessage_negativeMetric() {
        // when
        listener.onMessage(record(VALID.replace("\"filler_ratio\": 0.12", "\"filler_ratio\": -0.1")));

        // then
        verify(cognitiveMetricService, never()).record(any());
    }

    @Test
    @DisplayName("지원하지 않는 schema_version은 버린다")
    void onMessage_unsupportedSchemaVersion() {
        // when
        listener.onMessage(record(VALID.replace("\"schema_version\": 1", "\"schema_version\": 2")));

        // then
        verify(cognitiveMetricService, never()).record(any());
    }
}
