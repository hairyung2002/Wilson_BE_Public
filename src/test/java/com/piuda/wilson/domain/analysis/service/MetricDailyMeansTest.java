package com.piuda.wilson.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.analysis.entity.CognitiveMetric;
import com.piuda.wilson.domain.analysis.entity.JudgedMetric;
import com.piuda.wilson.domain.analysis.entity.SpeechMetrics;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetricDailyMeansTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static CognitiveMetric metric(String analyzedAt, Double latency, Double filler) {
        return CognitiveMetric.builder().turnId(UUID.randomUUID().toString()).subjectId(UUID.randomUUID())
                .analyzable(true)
                .metrics(new SpeechMetrics(null, null, filler, null, null, null, latency, null, null, null, null))
                .analyzedAt(Instant.parse(analyzedAt)).build();
    }

    @Test
    @DisplayName("지표를 KST 날짜별로 묶어 평균을 내고, 값이 없는 턴은 빼고 계산한다")
    void meansByKstDate() {
        // given: 2026-10-19T15:30Z = 10/20 00:30 KST
        MetricDailyMeans means = MetricDailyMeans.of(List.of(
                metric("2026-10-19T14:00:00Z", 800.0, 0.1),
                metric("2026-10-19T15:30:00Z", 900.0, null),
                metric("2026-10-20T03:00:00Z", 1100.0, 0.3)), KST);

        // when & then
        assertThat(means.meansFor(JudgedMetric.SUBSEQUENT_ONSET_LATENCY))
                .containsEntry(LocalDate.of(2026, 10, 19), 800.0)
                .containsEntry(LocalDate.of(2026, 10, 20), 1000.0);
        assertThat(means.meansFor(JudgedMetric.FILLER_RATIO))
                .containsEntry(LocalDate.of(2026, 10, 19), 0.1)
                .containsEntry(LocalDate.of(2026, 10, 20), 0.3);
        assertThat(means.meansFor(JudgedMetric.WORDS_PER_UTTERANCE)).isEmpty();
        assertThat(means.firstDate()).contains(LocalDate.of(2026, 10, 19));
    }

    @Test
    @DisplayName("분석할 수 없던 턴(지표 없음)은 건너뛴다")
    void skipsUnanalyzable() {
        // given
        CognitiveMetric unanalyzable = CognitiveMetric.builder().turnId("t-1").subjectId(UUID.randomUUID())
                .analyzable(false).analyzedAt(Instant.parse("2026-10-19T03:00:00Z")).build();

        // when
        MetricDailyMeans means = MetricDailyMeans.of(List.of(unanalyzable), KST);

        // then
        assertThat(means.meansFor(JudgedMetric.FILLER_RATIO)).isEmpty();
        assertThat(means.firstDate()).isEmpty();
    }
}
