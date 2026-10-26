package com.piuda.wilson.domain.analysis.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * 기준값 계산 (api-spec 12-2, 결정 D2).
 *
 * <ul>
 *   <li>지표가 처음 들어온 날부터 14일 동안의 일별 평균으로 평균·(모)표준편차를 만든다.</li>
 *   <li>그 14일 중 데이터가 있는 날이 7일 미만이면 7일째 데이터가 들어온 날까지 기간을 늘린다.</li>
 *   <li>표준편차가 평균의 5%보다 작으면 평균의 5%를 쓴다 (작은 흔들림을 이상으로 보지 않도록).</li>
 * </ul>
 */
public final class BaselineCalculator {

    static final int PERIOD_DAYS = 14;
    static final int MIN_DATA_DAYS = 7;
    static final double STD_FLOOR_RATIO = 0.05;

    private BaselineCalculator() {
    }

    /**
     * @param dailyMeans 한 지표의 날짜별 평균
     * @param firstDay   분석 가능한 지표가 처음 들어온 날
     * @param asOf       이 날까지 들어온 데이터로 계산한다 (판정하는 날)
     * @return 기간이 아직 차지 않았으면 empty
     */
    public static Optional<BaselineValue> calculate(NavigableMap<LocalDate, Double> dailyMeans, LocalDate firstDay,
                                                    LocalDate asOf) {
        LocalDate periodEnd = firstDay.plusDays(PERIOD_DAYS - 1);
        if (asOf.isBefore(periodEnd)) {
            return Optional.empty();
        }
        NavigableMap<LocalDate, Double> period = dailyMeans.subMap(firstDay, true, periodEnd, true);
        if (period.size() < MIN_DATA_DAYS) {
            NavigableMap<LocalDate, Double> upToAsOf = dailyMeans.subMap(firstDay, true, asOf, true);
            if (upToAsOf.size() < MIN_DATA_DAYS) {
                return Optional.empty();
            }
            LocalDate seventhDay = upToAsOf.keySet().stream().skip(MIN_DATA_DAYS - 1).findFirst().orElseThrow();
            periodEnd = seventhDay;
            period = dailyMeans.subMap(firstDay, true, periodEnd, true);
        }
        return Optional.of(of(period.values(), firstDay, periodEnd));
    }

    private static BaselineValue of(Collection<Double> values, LocalDate periodStart, LocalDate periodEnd) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance = values.stream().mapToDouble(value -> (value - mean) * (value - mean)).average().orElseThrow();
        double std = Math.max(Math.sqrt(variance), Math.abs(mean) * STD_FLOOR_RATIO);
        return new BaselineValue(mean, std, periodStart, periodEnd);
    }
}
