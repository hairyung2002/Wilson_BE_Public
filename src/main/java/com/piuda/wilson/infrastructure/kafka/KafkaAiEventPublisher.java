package com.piuda.wilson.infrastructure.kafka;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.piuda.wilson.infrastructure.ai.AiEventPublisher;
import com.piuda.wilson.infrastructure.ai.ConversationEndedEvent;
import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import com.piuda.wilson.infrastructure.ai.SubjectErasureEvent;
import com.piuda.wilson.infrastructure.ai.TurnAnalysisEvent;
import com.piuda.wilson.infrastructure.kafka.events.v1.ConversationEnded;
import com.piuda.wilson.infrastructure.kafka.events.v1.ConversationTurn;
import com.piuda.wilson.infrastructure.kafka.events.v1.SubjectErasureRequested;
import com.piuda.wilson.infrastructure.kafka.events.v1.TurnAnalysisRequested;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * BE → AI 이벤트를 Protobuf로 Kafka에 발행한다 (결정 J1~J3·J8, events.proto).
 *
 * <p>음성·대화 원문이 담기므로 로그에는 토픽과 식별자만 남긴다. 발행 실패로 대화가 멈추지 않도록
 * 예외는 밖으로 던지지 않는다.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class KafkaAiEventPublisher implements AiEventPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final AiEventProperties properties;

    @Override
    public void publishTurnAnalysis(TurnAnalysisEvent event) {
        TurnAnalysisRequested message = TurnAnalysisRequested.newBuilder()
                .setTraceId(event.traceId())
                .setTurnId(event.turnId())
                .setTargetUserId(event.subjectId())
                .setSessionId(String.valueOf(event.conversationId()))
                .setAudioPayload(ByteString.copyFrom(event.audio()))
                .setAudioFormat(event.audioFormat())
                .setSttText(event.sttText())
                .setLlmResponseText(event.llmResponseText() == null ? "" : event.llmResponseText())
                .setOccurredAt(toTimestamp(event.occurredAt()))
                .build();
        sendWithoutWaiting(properties.turnAnalysisTopic(), event.turnId(), message.toByteArray());
    }

    @Override
    public void publishConversationEnded(ConversationEndedEvent event) {
        ConversationEnded message = ConversationEnded.newBuilder()
                .setTraceId(event.traceId())
                .setTargetUserId(event.subjectId())
                .setSessionId(String.valueOf(event.conversationId()))
                .setStartedAt(toTimestamp(event.startedAt()))
                .setEndedAt(toTimestamp(event.endedAt()))
                .addAllTurns(event.turns().stream().map(KafkaAiEventPublisher::toConversationTurn).toList())
                .build();
        sendWithoutWaiting(properties.conversationEndedTopic(), String.valueOf(event.conversationId()),
                message.toByteArray());
    }

    @Override
    public boolean publishSubjectErasure(SubjectErasureEvent event) {
        byte[] value = SubjectErasureRequested.newBuilder()
                .setTraceId(event.traceId())
                .setTargetUserId(event.subjectId())
                .setRequestedAt(toTimestamp(event.requestedAt()))
                .build()
                .toByteArray();
        String topic = properties.subjectErasureTopic();
        try {
            kafkaTemplate.send(topic, event.subjectId(), value)
                    .get(properties.ackTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AI event publish interrupted: topic={}", topic);
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warn("AI event publish failed: topic={}, cause={}", topic, e.getClass().getSimpleName());
            return false;
        }
    }

    private void sendWithoutWaiting(String topic, String key, byte[] value) {
        try {
            kafkaTemplate.send(topic, key, value).whenComplete((result, failure) -> {
                if (failure != null) {
                    log.warn("AI event publish failed: topic={}, key={}, cause={}",
                            topic, key, failure.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException e) {
            log.warn("AI event publish failed: topic={}, key={}, cause={}", topic, key, e.getClass().getSimpleName());
        }
    }

    private static ConversationTurn toConversationTurn(DialogueHistoryTurn turn) {
        return ConversationTurn.newBuilder()
                .setTurnId(turn.turnId())
                .setSttText(turn.sttText())
                .setLlmResponseText(turn.llmResponseText())
                .build();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
