package com.piuda.wilson.domain.analysis.service;

import com.piuda.wilson.domain.analysis.entity.JudgedMetric;

/**
 * 하루 지표가 기준값에서 벗어났는지와 연속 횟수 판정 (api-spec 12-3, 결정 D3).
 *
 * <ul>
 *   <li>z = (일평균 − 기준평균) / 기준표준편차를 나빠지는 방향을 양수로 보고, z ≥ 2면 그날은 "벗어남"</li>
 *   <li>데이터가 있는 날 기준 5일 연속 벗어나면 그 지표를 판정한다. 대화가 없는 날은 연속 횟수를 유지한다.</li>
 * </ul>
 */
public final class DeviationJudge {

    static final double Z_THRESHOLD = 2.0;
    static final int STREAK_TO_FLAG = 5;

    private DeviationJudge() {
    }

    /** 기준 표준편차가 0이면(평소 값이 늘 0 등) 벗어난 정도를 잴 수 없으므로 null. */
    public static Double worseZ(JudgedMetric metric, BaselineValue baseline, double dayMean) {
        if (baseline.std() == 0) {
            return null;
        }
        double z = (dayMean - baseline.mean()) / baseline.std();
        return metric.isWorseWhenHigher() ? z : -z;
    }

    /** @param z 그날 데이터가 없었으면 null */
    public static int nextStreak(Double z, int previousStreak) {
        if (z == null) {
            return previousStreak;
        }
        return z >= Z_THRESHOLD ? previousStreak + 1 : 0;
    }

    public static boolean isFlagged(int streak) {
        return streak >= STREAK_TO_FLAG;
    }
}
