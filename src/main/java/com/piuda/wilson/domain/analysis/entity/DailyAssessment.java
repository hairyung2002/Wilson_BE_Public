package com.piuda.wilson.domain.analysis.entity;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.global.common.entity.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 노인의 하루 판정 (api-spec 12-3). 대화가 있던 날만 남는다.
 * 위험도는 지표별 판정에서 정해진다: 5일 연속 벗어난 지표 0개 LOW · 1개 MEDIUM · 2개 이상 HIGH.
 */
@Getter
@Entity
@Table(name = "daily_assessment", uniqueConstraints = @UniqueConstraint(
        name = "uk_daily_assessment_elderly_date", columnNames = {"elderly_id", "assessment_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyAssessment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "elderly_id", nullable = false, foreignKey = @ForeignKey(name = "fk_daily_assessment_elderly"))
    private Elderly elderly;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    private AssessmentLevel level;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "daily_assessment_metric", joinColumns = @JoinColumn(name = "assessment_id"),
            foreignKey = @ForeignKey(name = "fk_daily_assessment_metric_assessment"))
    private List<MetricJudgement> metrics = new ArrayList<>();

    @Builder
    private DailyAssessment(Elderly elderly, LocalDate assessmentDate, List<MetricJudgement> metrics) {
        this.elderly = elderly;
        this.assessmentDate = assessmentDate;
        this.metrics = new ArrayList<>(metrics);
        this.level = AssessmentLevel.ofFlaggedCount(metrics.stream().filter(MetricJudgement::isFlagged).count());
    }

    /** 이 날까지 그 지표가 연속해서 벗어난 횟수. 아직 판정한 적 없는 지표면 0. */
    public int streakOf(JudgedMetric metric) {
        return metrics.stream().filter(judgement -> judgement.metric() == metric)
                .mapToInt(MetricJudgement::streak).findFirst().orElse(0);
    }

    public List<JudgedMetric> flaggedMetrics() {
        return metrics.stream().filter(MetricJudgement::isFlagged).map(MetricJudgement::metric).toList();
    }
}
