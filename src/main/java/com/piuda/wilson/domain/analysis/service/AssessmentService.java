package com.piuda.wilson.domain.analysis.service;

import com.piuda.wilson.domain.analysis.entity.AssessmentLevel;
import com.piuda.wilson.domain.analysis.entity.DailyAssessment;
import com.piuda.wilson.domain.analysis.entity.ElderBaseline;
import com.piuda.wilson.domain.analysis.entity.JudgedMetric;
import com.piuda.wilson.domain.analysis.entity.MetricJudgement;
import com.piuda.wilson.domain.analysis.repository.CognitiveMetricRepository;
import com.piuda.wilson.domain.analysis.repository.DailyAssessmentRepository;
import com.piuda.wilson.domain.analysis.repository.ElderBaselineRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.service.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노인 한 명의 하루 판정 (api-spec 12-2·12-3). 매일 판정 배치가 노인마다 호출한다.
 *
 * <ol>
 *   <li>아직 기준값이 없는 지표는 처음 14일이 찼는지 보고 기준값을 만든다 (그날은 판정하지 않는다).</li>
 *   <li>그날의 일별 평균을 기준값과 비교해 지표별 z·연속 횟수를 정하고 위험도를 남긴다.</li>
 *   <li>위험도가 올라갔으면 보호자에게 PATTERN_CHANGE 알림을 보낸다.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final ElderlyRepository elderlyRepository;
    private final ElderBaselineRepository elderBaselineRepository;
    private final DailyAssessmentRepository dailyAssessmentRepository;
    private final CognitiveMetricRepository cognitiveMetricRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public void assess(Long elderlyId, LocalDate date) {
        Optional<Elderly> found = elderlyRepository.findByIdAndDeletedFalse(elderlyId);
        if (found.isEmpty() || dailyAssessmentRepository.existsByElderlyIdAndAssessmentDate(elderlyId, date)) {
            return;
        }
        Elderly elderly = found.get();
        Map<JudgedMetric, ElderBaseline> baselines = loadBaselines(elderly, date);
        baselines.values().removeIf(baseline -> !baseline.canJudge(date));
        if (baselines.isEmpty()) {
            return;
        }
        MetricDailyMeans day = MetricDailyMeans.of(cognitiveMetricRepository
                .findBySubjectIdAndAnalyzableTrueAndAnalyzedAtGreaterThanEqualAndAnalyzedAtLessThan(
                        elderly.getSubjectId(), startOf(date), startOf(date.plusDays(1))), zone());
        if (day.firstDate().isEmpty()) {
            return;
        }
        Optional<DailyAssessment> previous = dailyAssessmentRepository
                .findFirstByElderlyIdAndAssessmentDateLessThanOrderByAssessmentDateDesc(elderlyId, date);
        DailyAssessment assessment = DailyAssessment.builder()
                .elderly(elderly).assessmentDate(date).metrics(judge(baselines, day, date, previous)).build();
        dailyAssessmentRepository.save(assessment);
        log.info("Daily assessment: elderlyId={}, date={}, level={}", elderlyId, date, assessment.getLevel());
        notifyIfRaised(elderly, previous.map(DailyAssessment::getLevel).orElse(AssessmentLevel.LOW),
                assessment.getLevel());
    }

    /** 기존 기준값에 더해, 아직 없는 지표는 이번에 만들 수 있으면 만들어 저장한다. */
    private Map<JudgedMetric, ElderBaseline> loadBaselines(Elderly elderly, LocalDate date) {
        Map<JudgedMetric, ElderBaseline> baselines = new EnumMap<>(JudgedMetric.class);
        elderBaselineRepository.findByElderlyId(elderly.getId())
                .forEach(baseline -> baselines.put(baseline.getMetric(), baseline));
        if (baselines.size() == JudgedMetric.values().length) {
            return baselines;
        }
        MetricDailyMeans history = MetricDailyMeans.of(cognitiveMetricRepository
                .findBySubjectIdAndAnalyzableTrueAndAnalyzedAtLessThan(elderly.getSubjectId(),
                        startOf(date.plusDays(1))), zone());
        List<ElderBaseline> created = new ArrayList<>();
        history.firstDate().ifPresent(firstDay -> Arrays.stream(JudgedMetric.values())
                .filter(metric -> !baselines.containsKey(metric))
                .forEach(metric -> BaselineCalculator.calculate(history.meansFor(metric), firstDay, date)
                        .map(value -> ElderBaseline.of(elderly, metric, value))
                        .ifPresent(created::add)));
        if (!created.isEmpty()) {
            elderBaselineRepository.saveAll(created);
            created.forEach(baseline -> baselines.put(baseline.getMetric(), baseline));
            log.info("Baselines created: elderlyId={}, metrics={}", elderly.getId(),
                    created.stream().map(ElderBaseline::getMetric).toList());
        }
        return baselines;
    }

    private static List<MetricJudgement> judge(Map<JudgedMetric, ElderBaseline> baselines, MetricDailyMeans day,
                                               LocalDate date, Optional<DailyAssessment> previous) {
        return baselines.values().stream().map(baseline -> {
            JudgedMetric metric = baseline.getMetric();
            Double dayMean = day.meansFor(metric).get(date);
            Double z = dayMean == null ? null : DeviationJudge.worseZ(metric, baseline.toValue(), dayMean);
            int previousStreak = previous.map(assessment -> assessment.streakOf(metric)).orElse(0);
            return new MetricJudgement(metric, z, DeviationJudge.nextStreak(z, previousStreak));
        }).toList();
    }

    private void notifyIfRaised(Elderly elderly, AssessmentLevel previous, AssessmentLevel current) {
        if (!current.isHigherThan(previous)) {
            return;
        }
        notificationService.notifyCaregivers(elderly.getId(), NotificationType.PATTERN_CHANGE,
                elderly.getName() + "님의 대화 모습", current.message());
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(zone()).toInstant();
    }

    private ZoneId zone() {
        return clock.getZone();
    }
}
