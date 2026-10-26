package com.piuda.wilson.domain.analysis.service;

import com.piuda.wilson.domain.analysis.dto.CognitiveMetricMessage;
import com.piuda.wilson.domain.analysis.entity.CognitiveMetric;
import com.piuda.wilson.domain.analysis.entity.SpeechMetrics;
import com.piuda.wilson.domain.analysis.repository.CognitiveMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * AI 서버가 보낸 인지지표를 턴 단위로 한 번만 저장한다.
 *
 * <p>Kafka는 같은 메시지를 다시 보낼 수 있다(at-least-once). 이미 받은 턴은 건너뛰고,
 * 동시에 들어와 유니크 제약에 걸린 경우도 중복으로 본다. 제약 위반을 이 메서드 안에서 처리해야 하므로
 * 트랜잭션은 repository 저장 단위에 맡긴다 (메서드에 걸면 예외 후 커밋이 롤백 전용이 된다).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CognitiveMetricService {

    private final CognitiveMetricRepository cognitiveMetricRepository;

    public void record(CognitiveMetricMessage message) {
        String turnId = message.turnId().toString();
        if (cognitiveMetricRepository.existsByTurnId(turnId)) {
            log.info("Duplicate cognitive metric skipped: turnId={}", turnId);
            return;
        }
        try {
            cognitiveMetricRepository.save(toEntity(message));
            log.info("Cognitive metric recorded: turnId={}, sessionId={}, analyzable={}",
                    turnId, message.sessionId(), message.analyzable());
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate cognitive metric skipped (concurrent): turnId={}", turnId);
        }
    }

    private static CognitiveMetric toEntity(CognitiveMetricMessage message) {
        return CognitiveMetric.builder()
                .turnId(message.turnId().toString())
                .subjectId(message.subjectId())
                .analyzable(message.analyzable())
                .metrics(toSpeechMetrics(message.metrics()))
                .neural(rawJson(message.neural()))
                .emotion(rawJson(message.emotion()))
                .analyzedAt(message.analyzedAt().toInstant())
                .build();
    }

    private static SpeechMetrics toSpeechMetrics(CognitiveMetricMessage.Metrics metrics) {
        if (metrics == null) {
            return null;
        }
        return new SpeechMetrics(metrics.morphemeTokenCount(), metrics.utteranceCount(), metrics.fillerRatio(),
                metrics.emptySpeechRatio(), metrics.contentInfoUnitRatio(), metrics.wordsPerUtterance(),
                metrics.subsequentOnsetLatencyMs(), metrics.silentPauseRatio(), metrics.jitter(),
                metrics.shimmer(), metrics.cpp());
    }

    private static String rawJson(JsonNode node) {
        return node == null || node.isNull() ? null : node.toString();
    }
}
