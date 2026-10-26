package com.piuda.wilson.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.piuda.wilson.domain.analysis.entity.JudgedMetric;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviationJudgeTest {

    private static final BaselineValue BASELINE =
            new BaselineValue(100.0, 10.0, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14));

    @Test
    @DisplayName("z는 나빠지는 방향을 양수로 본다 (대답 시간은 늘수록, 말 길이는 줄수록 나쁨)")
    void worseZ() {
        // when & then
        assertThat(DeviationJudge.worseZ(JudgedMetric.SUBSEQUENT_ONSET_LATENCY, BASELINE, 125.0))
                .isCloseTo(2.5, within(1e-9));
        assertThat(DeviationJudge.worseZ(JudgedMetric.WORDS_PER_UTTERANCE, BASELINE, 125.0))
                .isCloseTo(-2.5, within(1e-9));
        assertThat(DeviationJudge.worseZ(JudgedMetric.WORDS_PER_UTTERANCE, BASELINE, 80.0))
                .isCloseTo(2.0, within(1e-9));
    }

    @Test
    @DisplayName("기준 표준편차가 0이면(평소 값이 늘 0) 판정하지 않는다")
    void worseZ_zeroStd() {
        // given
        BaselineValue flat = new BaselineValue(0.0, 0.0, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14));

        // when & then
        assertThat(DeviationJudge.worseZ(JudgedMetric.FILLER_RATIO, flat, 0.2)).isNull();
    }

    @Test
    @DisplayName("z ≥ 2면 연속 횟수를 1 늘리고, 아니면 0으로 돌리고, 그날 데이터가 없으면 그대로 둔다")
    void nextStreak() {
        // when & then
        assertThat(DeviationJudge.nextStreak(2.0, 3)).isEqualTo(4);
        assertThat(DeviationJudge.nextStreak(1.99, 3)).isZero();
        assertThat(DeviationJudge.nextStreak(null, 3)).isEqualTo(3);
    }

    @Test
    @DisplayName("데이터가 있는 날 기준 5일 연속 벗어나면 그 지표를 판정한다")
    void isFlagged() {
        // when & then
        assertThat(DeviationJudge.isFlagged(4)).isFalse();
        assertThat(DeviationJudge.isFlagged(5)).isTrue();
    }
}
