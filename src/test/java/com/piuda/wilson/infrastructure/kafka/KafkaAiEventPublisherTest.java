package com.piuda.wilson.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.infrastructure.ai.ConversationEndedEvent;
import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import com.piuda.wilson.infrastructure.ai.SubjectErasureEvent;
import com.piuda.wilson.infrastructure.ai.TurnAnalysisEvent;
import com.piuda.wilson.infrastructure.kafka.events.v1.ConversationEnded;
import com.piuda.wilson.infrastructure.kafka.events.v1.SubjectErasureRequested;
import com.piuda.wilson.infrastructure.kafka.events.v1.TurnAnalysisRequested;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaAiEventPublisherTest {

    private static final AiEventProperties PROPERTIES = new AiEventProperties(true, "turn-topic", "ended-topic",
            "erasure-topic", Duration.ofMillis(200), 1, 1);
    private static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z");
    private static final String SUBJECT_ID = "3f9c1c9e-6d2a-4b8e-9a51-2a7c0f1b8d10";

    @Mock
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    private KafkaAiEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaAiEventPublisher(kafkaTemplate, PROPERTIES);
    }

    private byte[] sentValue(String topic, String key) {
        ArgumentCaptor<byte[]> value = ArgumentCaptor.forClass(byte[].class);
        verify(kafkaTemplate).send(eq(topic), eq(key), value.capture());
        return value.getValue();
    }

    @Test
    @DisplayName("턴 분석 요청은 turn_id를 키로 음성·발화·답변을 Protobuf로 보낸다 (J1)")
    void publishTurnAnalysis() throws Exception {
        // given
        given(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .willReturn(new CompletableFuture<>());
        TurnAnalysisEvent event = new TurnAnalysisEvent("trace-1", "turn-1", SUBJECT_ID, 15L, new byte[]{1, 2},
                "wav", "밥 먹었어", null, NOW);

        // when
        publisher.publishTurnAnalysis(event);

        // then
        TurnAnalysisRequested sent = TurnAnalysisRequested.parseFrom(sentValue("turn-topic", "turn-1"));
        assertThat(sent.getTraceId()).isEqualTo("trace-1");
        assertThat(sent.getTurnId()).isEqualTo("turn-1");
        assertThat(sent.getTargetUserId()).isEqualTo(SUBJECT_ID);
        assertThat(sent.getSessionId()).isEqualTo("15");
        assertThat(sent.getAudioPayload().toByteArray()).containsExactly(1, 2);
        assertThat(sent.getAudioFormat()).isEqualTo("wav");
        assertThat(sent.getSttText()).isEqualTo("밥 먹었어");
        assertThat(sent.getLlmResponseText()).as("답변이 없으면 빈 값").isEmpty();
        assertThat(sent.getOccurredAt().getSeconds()).isEqualTo(NOW.getEpochSecond());
    }

    @Test
    @DisplayName("대화 종료는 session_id를 키로 턴 텍스트를 오래된 순으로 보낸다 (J2)")
    void publishConversationEnded() throws Exception {
        // given
        given(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .willReturn(new CompletableFuture<>());
        ConversationEndedEvent event = new ConversationEndedEvent("trace-2", SUBJECT_ID, 15L, NOW.minusSeconds(600),
                NOW, List.of(new DialogueHistoryTurn("turn-g", "", "할머니, 오늘 점심은 뭐 드셨어요?"),
                new DialogueHistoryTurn("turn-1", "된장찌개", "맛있으셨겠어요")));

        // when
        publisher.publishConversationEnded(event);

        // then
        ConversationEnded sent = ConversationEnded.parseFrom(sentValue("ended-topic", "15"));
        assertThat(sent.getTargetUserId()).isEqualTo(SUBJECT_ID);
        assertThat(sent.getSessionId()).isEqualTo("15");
        assertThat(sent.getStartedAt().getSeconds()).isEqualTo(NOW.minusSeconds(600).getEpochSecond());
        assertThat(sent.getEndedAt().getSeconds()).isEqualTo(NOW.getEpochSecond());
        assertThat(sent.getTurnsList()).extracting(turn -> turn.getTurnId() + "|" + turn.getSttText() + "|"
                        + turn.getLlmResponseText())
                .containsExactly("turn-g||할머니, 오늘 점심은 뭐 드셨어요?", "turn-1|된장찌개|맛있으셨겠어요");
    }

    @Test
    @DisplayName("비동기 발행이 브로커 문제로 바로 실패해도 예외를 던지지 않는다 (대화는 계속)")
    void publishAsync_neverThrows() {
        // given
        given(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .willThrow(new KafkaException("metadata timeout"));
        TurnAnalysisEvent event = new TurnAnalysisEvent("trace-1", "turn-1", SUBJECT_ID, 15L, new byte[]{1},
                "wav", "밥 먹었어", "잘하셨어요", NOW);

        // when & then
        assertThatCode(() -> publisher.publishTurnAnalysis(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("파기 요청은 target_user_id를 키로 보내고, 브로커가 받으면 true를 돌려준다 (J3)")
    void publishSubjectErasure() throws Exception {
        // given
        given(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .willReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));

        // when
        boolean published = publisher.publishSubjectErasure(new SubjectErasureEvent("trace-3", SUBJECT_ID, NOW));

        // then
        assertThat(published).isTrue();
        SubjectErasureRequested sent = SubjectErasureRequested.parseFrom(sentValue("erasure-topic", SUBJECT_ID));
        assertThat(sent.getTargetUserId()).isEqualTo(SUBJECT_ID);
        assertThat(sent.getRequestedAt().getSeconds()).isEqualTo(NOW.getEpochSecond());
    }

    @Test
    @DisplayName("파기 요청을 브로커가 받지 못했거나 제한 시간 안에 답이 없으면 false를 돌려준다 (다시 보내도록)")
    void publishSubjectErasure_failed() {
        // given
        given(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .willReturn(CompletableFuture.failedFuture(new TimeoutException("broker down")))
                .willReturn(new CompletableFuture<>());
        SubjectErasureEvent event = new SubjectErasureEvent("trace-3", SUBJECT_ID, NOW);

        // when & then
        assertThat(publisher.publishSubjectErasure(event)).isFalse();
        assertThat(publisher.publishSubjectErasure(event)).as("응답 없음(제한 시간 초과)").isFalse();
    }
}
