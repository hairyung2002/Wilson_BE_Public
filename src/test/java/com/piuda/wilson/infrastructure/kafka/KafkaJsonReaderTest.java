package com.piuda.wilson.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;

class KafkaJsonReaderTest {

    private final KafkaJsonReader reader = new KafkaJsonReader(JsonMapper.builder().build(),
            Validation.buildDefaultValidatorFactory().getValidator());

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Sample(@NotBlank String sessionId, @Size(max = 5) String text) {
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("topic", 0, 3L, "key", value);
    }

    @Test
    @DisplayName("snake_case JSON을 읽고 모르는 필드는 무시한다")
    void read_valid() {
        // when & then
        assertThat(reader.read(record("{\"session_id\":\"15\",\"text\":\"안녕\",\"later\":1}"), Sample.class))
                .contains(new Sample("15", "안녕"));
    }

    @Test
    @DisplayName("JSON이 깨졌거나 검증에 실패하면 비어 있는 결과를 돌려준다 (다시 읽어도 실패하므로 버린다)")
    void read_invalid() {
        // when & then
        assertThat(reader.read(record("{not-json"), Sample.class)).isEmpty();
        assertThat(reader.read(record("{\"text\":\"안녕\"}"), Sample.class)).isEmpty();
        assertThat(reader.read(record("{\"session_id\":\"15\",\"text\":\"너무 긴 문장\"}"), Sample.class)).isEmpty();
    }
}
