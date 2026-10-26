package com.piuda.wilson.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.analysis.dto.CognitiveMetricMessage;
import com.piuda.wilson.domain.analysis.entity.CognitiveMetric;
import com.piuda.wilson.domain.analysis.entity.SpeechMetrics;
import com.piuda.wilson.domain.analysis.repository.CognitiveMetricRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

@ExtendWith(MockitoExtension.class)
class CognitiveMetricServiceTest {

    private static final UUID TURN_ID = UUID.fromString("ee84a22f-dba5-42ba-a6af-7416d2276dc3");
    private static final UUID SUBJECT_ID = UUID.fromString("0ef3e83e-9b1d-4572-9b78-4221ccaf936e");

    @Mock
    private CognitiveMetricRepository cognitiveMetricRepository;

    @InjectMocks
    private CognitiveMetricService cognitiveMetricService;

    private static CognitiveMetricMessage message() {
        return new CognitiveMetricMessage(1, TURN_ID, SUBJECT_ID, "15", "trace-1",
                OffsetDateTime.parse("2026-10-20T10:15:30+09:00"), true,
                new CognitiveMetricMessage.Metrics(42, 3, 0.12, 0.05, 0.4, 14.0, 820.0, 0.18, 0.011, 0.042, 12.3),
                JsonMapper.builder().build().readTree("{\"score\":0.3}"), NullNode.getInstance());
    }

    @Test
    @DisplayName("처음 받은 턴의 지표를 가명 식별자 기준으로 저장한다")
    void record_new() {
        // given
        given(cognitiveMetricRepository.existsByTurnId(TURN_ID.toString())).willReturn(false);

        // when
        cognitiveMetricService.record(message());

        // then
        ArgumentCaptor<CognitiveMetric> captor = ArgumentCaptor.forClass(CognitiveMetric.class);
        verify(cognitiveMetricRepository).save(captor.capture());
        CognitiveMetric saved = captor.getValue();
        assertThat(saved.getTurnId()).isEqualTo(TURN_ID.toString());
        assertThat(saved.getSubjectId()).isEqualTo(SUBJECT_ID);
        assertThat(saved.isAnalyzable()).isTrue();
        assertThat(saved.getMetrics())
                .isEqualTo(new SpeechMetrics(42, 3, 0.12, 0.05, 0.4, 14.0, 820.0, 0.18, 0.011, 0.042, 12.3));
        assertThat(saved.getNeural()).isEqualTo("{\"score\":0.3}");
        assertThat(saved.getEmotion()).as("JSON null은 저장하지 않는다").isNull();
        assertThat(saved.getAnalyzedAt()).isEqualTo(OffsetDateTime.parse("2026-10-20T10:15:30+09:00").toInstant());
    }

    @Test
    @DisplayName("이미 저장된 턴이면 다시 저장하지 않는다 (at-least-once 중복 수신)")
    void record_duplicate() {
        // given
        given(cognitiveMetricRepository.existsByTurnId(TURN_ID.toString())).willReturn(true);

        // when
        cognitiveMetricService.record(message());

        // then
        verify(cognitiveMetricRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시에 같은 턴이 저장돼 유니크 제약에 걸려도 중복으로 보고 넘어간다")
    void record_concurrentDuplicate() {
        // given
        given(cognitiveMetricRepository.existsByTurnId(TURN_ID.toString())).willReturn(false);
        given(cognitiveMetricRepository.save(any())).willThrow(new DataIntegrityViolationException("uk"));

        // when & then (예외 없음)
        cognitiveMetricService.record(message());
    }
}
