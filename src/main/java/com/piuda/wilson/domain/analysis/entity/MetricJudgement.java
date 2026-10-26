package com.piuda.wilson.domain.analysis.entity;

import com.piuda.wilson.domain.analysis.service.DeviationJudge;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * 하루 판정 중 지표 하나의 결과. zScore는 그날 그 지표 데이터가 없었으면 null이고,
 * streak은 데이터가 있는 날 기준으로 연속해서 벗어난 횟수다.
 */
@Embeddable
public record MetricJudgement(
        @Enumerated(EnumType.STRING) @Column(name = "metric", nullable = false, length = 40) JudgedMetric metric,
        @Column(name = "z_score") Double zScore,
        @Column(name = "streak", nullable = false) int streak
) {

    public boolean isFlagged() {
        return DeviationJudge.isFlagged(streak);
    }
}
