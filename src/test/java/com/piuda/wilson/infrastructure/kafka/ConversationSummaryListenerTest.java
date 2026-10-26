package com.piuda.wilson.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.conversation.dto.ConversationSummaryMessage;
import com.piuda.wilson.domain.conversation.service.ConversationSummaryService;
import jakarta.validation.Validation;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class ConversationSummaryListenerTest {

    private static final String VALID = """
            {
              "schema_version": 1,
              "session_id": "15",
              "target_user_id": "3f9c1c9e-6d2a-4b8e-9a51-2a7c0f1b8d10",
              "summary": "점심으로 된장찌개를 드셨고, 오후에 산책을 다녀오실 계획을 이야기하셨어요.",
              "generated_at": "2026-10-22T03:12:45Z",
              "added_later_by_ai": "ignored"
            }
            """;

    private final ConversationSummaryService conversationSummaryService = mock(ConversationSummaryService.class);
    private final ConversationSummaryListener listener = new ConversationSummaryListener(new KafkaJsonReader(
            JsonMapper.builder().build(), Validation.buildDefaultValidatorFactory().getValidator()),
            conversationSummaryService);

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("wilson.conversation-summaries.v1", 0, 7L, "15", value);
    }

    @Test
    @DisplayName("계약대로 온 요약을 읽어 저장을 요청한다 (모르는 필드는 무시)")
    void onMessage_valid() {
        // when
        listener.onMessage(record(VALID));

        // then
        ArgumentCaptor<ConversationSummaryMessage> captor = ArgumentCaptor.forClass(ConversationSummaryMessage.class);
        verify(conversationSummaryService).record(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo("15");
        assertThat(captor.getValue().targetUserId()).isEqualTo(UUID.fromString("3f9c1c9e-6d2a-4b8e-9a51-2a7c0f1b8d10"));
    }

    @Test
    @DisplayName("200자를 넘는 요약, 숫자가 아닌 session_id, 지원하지 않는 schema_version은 버린다")
    void onMessage_invalid() {
        // when
        listener.onMessage(record(VALID.replaceAll("\"summary\": \".*?\"", "\"summary\": \"" + "가".repeat(201) + "\"")));
        listener.onMessage(record(VALID.replace("\"session_id\": \"15\"", "\"session_id\": \"abc\"")));
        listener.onMessage(record(VALID.replace("\"schema_version\": 1", "\"schema_version\": 2")));
        listener.onMessage(record("{not-json"));

        // then
        verify(conversationSummaryService, never()).record(any());
    }
}
