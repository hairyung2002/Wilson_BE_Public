package com.piuda.wilson.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * AI 서버가 Kafka로 보내는 턴 단위 인지지표 메시지 (계약: docs/kafka-cognitive-metrics.md, schema_version 1).
 *
 * <p>AI 쪽이 필드를 추가해도 BE가 깨지지 않도록 모르는 필드는 무시한다.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CognitiveMetricMessage(
        @NotNull Integer schemaVersion,
        @NotNull UUID turnId,
        @NotNull UUID subjectId,
        @NotBlank String sessionId,
        String traceId,
        @NotNull OffsetDateTime analyzedAt,
        @NotNull Boolean analyzable,
        @Valid Metrics metrics,
        JsonNode neural,
        JsonNode emotion
) {

    @JsonIgnore
    @AssertTrue(message = "analyzable이 true이면 metrics가 있어야 합니다.")
    public boolean isMetricsPresentWhenAnalyzable() {
        return !Boolean.TRUE.equals(analyzable) || metrics != null;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metrics(
            @PositiveOrZero Integer morphemeTokenCount,
            @PositiveOrZero Integer utteranceCount,
            @PositiveOrZero Double fillerRatio,
            @PositiveOrZero Double emptySpeechRatio,
            @PositiveOrZero Double contentInfoUnitRatio,
            @PositiveOrZero Double wordsPerUtterance,
            @PositiveOrZero Double subsequentOnsetLatencyMs,
            @PositiveOrZero Double silentPauseRatio,
            @PositiveOrZero Double jitter,
            @PositiveOrZero Double shimmer,
            Double cpp
    ) {
    }
}
