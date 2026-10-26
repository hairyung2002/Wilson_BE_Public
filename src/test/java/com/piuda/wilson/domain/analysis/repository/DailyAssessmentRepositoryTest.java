package com.piuda.wilson.domain.analysis.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.piuda.wilson.domain.analysis.entity.AssessmentLevel;
import com.piuda.wilson.domain.analysis.entity.DailyAssessment;
import com.piuda.wilson.domain.analysis.entity.ElderBaseline;
import com.piuda.wilson.domain.analysis.entity.JudgedMetric;
import com.piuda.wilson.domain.analysis.entity.MetricJudgement;
import com.piuda.wilson.domain.analysis.service.BaselineValue;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class DailyAssessmentRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DailyAssessmentRepository dailyAssessmentRepository;

    @Autowired
    private ElderBaselineRepository elderBaselineRepository;

    private Elderly elderly;
    private Elderly other;

    @BeforeEach
    void setUp() {
        elderly = entityManager.persist(Elderly.builder().name("김철수").build());
        other = entityManager.persist(Elderly.builder().name("이영희").build());
    }

    private DailyAssessment assessment(Elderly target, LocalDate date, int latencyStreak) {
        return DailyAssessment.builder().elderly(target).assessmentDate(date)
                .metrics(List.of(new MetricJudgement(JudgedMetric.SUBSEQUENT_ONSET_LATENCY, 2.5, latencyStreak),
                        new MetricJudgement(JudgedMetric.FILLER_RATIO, null, 0)))
                .build();
    }

    @Test
    @DisplayName("판정일 이전의 가장 최근 판정을 찾는다 (대화가 없던 날은 판정이 없다)")
    void findLatestBefore() {
        // given
        dailyAssessmentRepository.save(assessment(elderly, DAY.minusDays(5), 1));
        dailyAssessmentRepository.save(assessment(elderly, DAY.minusDays(2), 2));
        dailyAssessmentRepository.save(assessment(elderly, DAY, 3));
        dailyAssessmentRepository.save(assessment(other, DAY.minusDays(1), 9));
        entityManager.flush();
        entityManager.clear();

        // when
        DailyAssessment latest = dailyAssessmentRepository
                .findFirstByElderlyIdAndAssessmentDateLessThanOrderByAssessmentDateDesc(elderly.getId(), DAY)
                .orElseThrow();

        // then
        assertThat(latest.getAssessmentDate()).isEqualTo(DAY.minusDays(2));
        assertThat(latest.streakOf(JudgedMetric.SUBSEQUENT_ONSET_LATENCY)).isEqualTo(2);
        assertThat(dailyAssessmentRepository.existsByElderlyIdAndAssessmentDate(elderly.getId(), DAY)).isTrue();
        assertThat(dailyAssessmentRepository.existsByElderlyIdAndAssessmentDate(other.getId(), DAY)).isFalse();
    }

    @Test
    @DisplayName("기간 안의 판정을 지표별 판정과 함께 날짜순으로 조회한다")
    void findBetween() {
        // given
        dailyAssessmentRepository.save(assessment(elderly, DAY, 5));
        dailyAssessmentRepository.save(assessment(elderly, DAY.minusDays(1), 4));
        dailyAssessmentRepository.save(assessment(elderly, DAY.minusDays(30), 0));
        entityManager.flush();
        entityManager.clear();

        // when
        List<DailyAssessment> found = dailyAssessmentRepository
                .findByElderlyIdAndAssessmentDateBetweenOrderByAssessmentDateAsc(elderly.getId(), DAY.minusDays(7), DAY);

        // then
        assertThat(found).extracting(DailyAssessment::getAssessmentDate).containsExactly(DAY.minusDays(1), DAY);
        assertThat(found).extracting(DailyAssessment::getLevel)
                .containsExactly(AssessmentLevel.LOW, AssessmentLevel.MEDIUM);
        assertThat(found.get(1).getMetrics()).hasSize(2);
    }

    @Test
    @DisplayName("대시보드용: 가장 최근 판정, 다른 위험도였던 마지막 판정, 그 다음 판정, 첫 판정을 찾는다")
    void levelRunQueries() {
        // given: LOW(10/14) → MEDIUM(10/15) → LOW(10/16) → MEDIUM(10/18, 10/19)
        dailyAssessmentRepository.save(assessment(elderly, DAY.minusDays(6), 0));
        dailyAssessmentRepository.save(assessment(elderly, DAY.minusDays(5), 5));
        dailyAssessmentRepository.save(assessment(elderly, DAY.minusDays(4), 0));
        dailyAssessmentRepository.save(assessment(elderly, DAY.minusDays(2), 5));
        dailyAssessmentRepository.save(assessment(elderly, DAY.minusDays(1), 6));
        dailyAssessmentRepository.save(assessment(other, DAY, 0));
        entityManager.flush();
        entityManager.clear();
        Long id = elderly.getId();

        // when & then
        assertThat(dailyAssessmentRepository.findFirstByElderlyIdOrderByAssessmentDateDesc(id).orElseThrow()
                .getAssessmentDate()).isEqualTo(DAY.minusDays(1));
        assertThat(dailyAssessmentRepository.findFirstByElderlyIdAndLevelNotOrderByAssessmentDateDesc(
                id, AssessmentLevel.MEDIUM).orElseThrow().getAssessmentDate()).isEqualTo(DAY.minusDays(4));
        assertThat(dailyAssessmentRepository.findFirstByElderlyIdAndAssessmentDateGreaterThanOrderByAssessmentDateAsc(
                id, DAY.minusDays(4)).orElseThrow().getAssessmentDate()).isEqualTo(DAY.minusDays(2));
        assertThat(dailyAssessmentRepository.findFirstByElderlyIdOrderByAssessmentDateAsc(id).orElseThrow()
                .getAssessmentDate()).isEqualTo(DAY.minusDays(6));
    }

    @Test
    @DisplayName("노인·날짜별 판정은 하나뿐이다 (배치가 두 번 돌아도 중복 저장되지 않게)")
    void uniquePerDay() {
        // given
        dailyAssessmentRepository.saveAndFlush(assessment(elderly, DAY, 1));

        // when & then
        assertThatThrownBy(() -> dailyAssessmentRepository.saveAndFlush(assessment(elderly, DAY, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("노인을 삭제하면 판정 이력(지표별 판정 포함)과 기준값을 지운다")
    void deleteByElderlyId() {
        // given
        dailyAssessmentRepository.save(assessment(elderly, DAY, 1));
        dailyAssessmentRepository.save(assessment(other, DAY, 1));
        BaselineValue value = new BaselineValue(800.0, 100.0, DAY.minusDays(30), DAY.minusDays(17));
        elderBaselineRepository.save(ElderBaseline.of(elderly, JudgedMetric.SUBSEQUENT_ONSET_LATENCY, value));
        elderBaselineRepository.save(ElderBaseline.of(other, JudgedMetric.SUBSEQUENT_ONSET_LATENCY, value));
        entityManager.flush();
        entityManager.clear();

        // when
        dailyAssessmentRepository.deleteByElderlyId(elderly.getId());
        elderBaselineRepository.deleteByElderlyId(elderly.getId());
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(dailyAssessmentRepository.findAll()).extracting(a -> a.getElderly().getId())
                .containsExactly(other.getId());
        assertThat(elderBaselineRepository.findByElderlyId(elderly.getId())).isEmpty();
        assertThat(elderBaselineRepository.findByElderlyId(other.getId())).hasSize(1);
    }

    @Test
    @DisplayName("노인·지표별 기준값은 하나뿐이다 (한 번 만들면 고정)")
    void baselineUnique() {
        // given
        BaselineValue value = new BaselineValue(800.0, 100.0, DAY.minusDays(30), DAY.minusDays(17));
        elderBaselineRepository.saveAndFlush(ElderBaseline.of(elderly, JudgedMetric.FILLER_RATIO, value));

        // when & then
        assertThatThrownBy(() -> elderBaselineRepository.saveAndFlush(
                ElderBaseline.of(elderly, JudgedMetric.FILLER_RATIO, value)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
