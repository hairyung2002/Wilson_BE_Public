package com.piuda.wilson.domain.analysis.entity;

import com.piuda.wilson.domain.analysis.service.BaselineValue;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 노인·지표별 기준값 (api-spec 12-2, 결정 D2). 한 번 만들면 바꾸지 않는다
 * (천천히 나빠지는 변화를 기준값이 따라가 버리지 않도록).
 */
@Getter
@Entity
@Table(name = "elder_baseline", uniqueConstraints = @UniqueConstraint(
        name = "uk_elder_baseline_elderly_metric", columnNames = {"elderly_id", "metric"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ElderBaseline extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "elderly_id", nullable = false, foreignKey = @ForeignKey(name = "fk_elder_baseline_elderly"))
    private Elderly elderly;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private JudgedMetric metric;

    @Column(nullable = false)
    private double mean;

    @Column(nullable = false)
    private double std;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    private ElderBaseline(Elderly elderly, JudgedMetric metric, BaselineValue value) {
        this.elderly = elderly;
        this.metric = metric;
        this.mean = value.mean();
        this.std = value.std();
        this.periodStart = value.periodStart();
        this.periodEnd = value.periodEnd();
    }

    public static ElderBaseline of(Elderly elderly, JudgedMetric metric, BaselineValue value) {
        return new ElderBaseline(elderly, metric, value);
    }

    public BaselineValue toValue() {
        return new BaselineValue(mean, std, periodStart, periodEnd);
    }

    /** 기준값을 만든 기간의 날은 기준값 자신과 비교하게 되므로 판정하지 않는다. */
    public boolean canJudge(LocalDate date) {
        return periodEnd.isBefore(date);
    }
}
