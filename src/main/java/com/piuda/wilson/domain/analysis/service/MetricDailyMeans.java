package com.piuda.wilson.domain.analysis.service;

import com.piuda.wilson.domain.analysis.entity.CognitiveMetric;
import com.piuda.wilson.domain.analysis.entity.JudgedMetric;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 턴 단위 지표를 판정 지표별·날짜(KST)별 평균으로 묶는다. 분석할 수 없던 턴과 계산되지 않은 값(null)은 뺀다.
 */
public final class MetricDailyMeans {

    private final Map<JudgedMetric, NavigableMap<LocalDate, Double>> means;
    private final LocalDate firstDate;

    private MetricDailyMeans(Map<JudgedMetric, NavigableMap<LocalDate, Double>> means, LocalDate firstDate) {
        this.means = means;
        this.firstDate = firstDate;
    }

    public static MetricDailyMeans of(List<CognitiveMetric> metrics, ZoneId zone) {
        List<CognitiveMetric> analyzable = metrics.stream()
                .filter(metric -> metric.isAnalyzable() && metric.getMetrics() != null)
                .toList();
        Map<JudgedMetric, NavigableMap<LocalDate, Double>> means = new EnumMap<>(JudgedMetric.class);
        for (JudgedMetric judged : JudgedMetric.values()) {
            means.put(judged, analyzable.stream()
                    .filter(metric -> judged.valueOf(metric.getMetrics()) != null)
                    .collect(Collectors.groupingBy(metric -> date(metric, zone), TreeMap::new,
                            Collectors.averagingDouble(metric -> judged.valueOf(metric.getMetrics())))));
        }
        LocalDate firstDate = analyzable.stream().map(metric -> date(metric, zone))
                .min(LocalDate::compareTo).orElse(null);
        return new MetricDailyMeans(means, firstDate);
    }

    private static LocalDate date(CognitiveMetric metric, ZoneId zone) {
        return metric.getAnalyzedAt().atZone(zone).toLocalDate();
    }

    public NavigableMap<LocalDate, Double> meansFor(JudgedMetric metric) {
        return means.get(metric);
    }

    /** 분석 가능한 지표가 처음 들어온 날. 기준값 기간의 시작이다. */
    public Optional<LocalDate> firstDate() {
        return Optional.ofNullable(firstDate);
    }
}
