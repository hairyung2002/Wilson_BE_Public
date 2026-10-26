package com.piuda.wilson.domain.conversation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * AI 워커가 Kafka로 보내는 보호자용 대화 요약 (계약: docs/ai-integration-contract.md 4장, schema_version 1).
 *
 * <p>sessionId는 BE 대화 ID, targetUserId는 노인 가명 식별자다. AI 쪽이 필드를 추가해도 깨지지 않도록
 * 모르는 필드는 무시한다.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConversationSummaryMessage(
        @NotNull Integer schemaVersion,
        @NotBlank @Pattern(regexp = "\\d{1,18}") String sessionId,
        @NotNull UUID targetUserId,
        @NotBlank @Size(max = 200) String summary,
        @NotNull OffsetDateTime generatedAt
) {
}
