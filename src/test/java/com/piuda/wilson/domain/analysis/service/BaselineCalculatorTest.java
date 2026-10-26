package com.piuda.wilson.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaselineCalculatorTest {

    private static final LocalDate FIRST = LocalDate.of(2026, 10, 1);

    private static TreeMap<LocalDate, Double> days(int count, double... values) {
        TreeMap<LocalDate, Double> means = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            means.put(FIRST.plusDays(i), values[i % values.length]);
        }
        return means;
    }

    @Test
    @DisplayName("처음 14일이 지나기 전에는 기준값을 만들지 않는다")
    void notYet() {
        // when & then
        assertThat(BaselineCalculator.calculate(days(13, 100), FIRST, FIRST.plusDays(12))).isEmpty();
    }

    @Test
    @DisplayName("처음 14일의 일별 평균으로 평균·표준편차를 만든다")
    void fourteenDays() {
        // when
        BaselineValue baseline = BaselineCalculator.calculate(days(20, 90, 110), FIRST, FIRST.plusDays(13)).orElseThrow();

        // then
        assertThat(baseline.mean()).isEqualTo(100.0);
        assertThat(baseline.std()).isCloseTo(10.0, within(1e-9));
        assertThat(baseline.periodStart()).isEqualTo(FIRST);
        assertThat(baseline.periodEnd()).isEqualTo(FIRST.plusDays(13));
    }

    @Test
    @DisplayName("14일 중 데이터가 있는 날이 7일 미만이면, 7일이 될 때까지 기간을 늘린다")
    void extendUntilSevenDays() {
        // given: 이틀에 한 번만 대화 (14일 동안 7일 미만이 되도록 사흘 간격)
        TreeMap<LocalDate, Double> sparse = new TreeMap<>();
        for (int i = 0; i < 8; i++) {
            sparse.put(FIRST.plusDays(i * 3L), 100.0);
        }

        // when & then
        assertThat(BaselineCalculator.calculate(sparse, FIRST, FIRST.plusDays(13))).isEmpty();
        BaselineValue baseline = BaselineCalculator.calculate(sparse, FIRST, FIRST.plusDays(18)).orElseThrow();
        assertThat(baseline.periodEnd()).isEqualTo(FIRST.plusDays(18));
    }

    @Test
    @DisplayName("표준편차가 평균의 5%보다 작으면 평균의 5%를 쓴다 (작은 흔들림을 이상으로 보지 않도록)")
    void stdFloor() {
        // when
        BaselineValue baseline = BaselineCalculator.calculate(days(14, 100, 100.1), FIRST, FIRST.plusDays(13))
                .orElseThrow();

        // then
        assertThat(baseline.std()).isCloseTo(5.0025, within(1e-9));
    }
}
