package com.piuda.wilson.domain.analysis.dto;

import com.piuda.wilson.domain.analysis.entity.AssessmentLevel;
import com.piuda.wilson.domain.analysis.entity.DailyAssessment;
import com.piuda.wilson.domain.analysis.entity.JudgedMetric;
import java.time.LocalDate;
import java.util.List;

/** 판정 이력 한 건 (api-spec 12-5). 보호자 화면에는 level이 아닌 message가 보인다. */
public record AssessmentResponse(LocalDate date, AssessmentLevel level, String message,
                                 List<FlaggedMetricResponse> flaggedMetrics) {

    public record FlaggedMetricResponse(String metric, String label) {

        public static FlaggedMetricResponse from(JudgedMetric metric) {
            return new FlaggedMetricResponse(metric.code(), metric.label());
        }
    }

    public static AssessmentResponse from(DailyAssessment assessment) {
        return new AssessmentResponse(assessment.getAssessmentDate(), assessment.getLevel(),
                assessment.getLevel().message(),
                assessment.flaggedMetrics().stream().map(FlaggedMetricResponse::from).toList());
    }
}
