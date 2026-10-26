package com.piuda.wilson.domain.analysis.repository;

import com.piuda.wilson.domain.analysis.entity.AssessmentLevel;
import com.piuda.wilson.domain.analysis.entity.DailyAssessment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyAssessmentRepository extends JpaRepository<DailyAssessment, Long> {

    boolean existsByElderlyIdAndAssessmentDate(Long elderlyId, LocalDate assessmentDate);

    /** 연속 횟수를 이어 가기 위한 직전 판정. 대화가 없던 날은 판정이 없으므로 "전날"이 아닐 수 있다. */
    @EntityGraph(attributePaths = "metrics")
    Optional<DailyAssessment> findFirstByElderlyIdAndAssessmentDateLessThanOrderByAssessmentDateDesc(
            Long elderlyId, LocalDate assessmentDate);

    @EntityGraph(attributePaths = "metrics")
    List<DailyAssessment> findByElderlyIdAndAssessmentDateBetweenOrderByAssessmentDateAsc(
            Long elderlyId, LocalDate from, LocalDate to);

    /* 대시보드(api-spec 13): 지금 위험도와, 그 위험도가 시작된 첫 판정일(since)을 찾는다. */

    Optional<DailyAssessment> findFirstByElderlyIdOrderByAssessmentDateDesc(Long elderlyId);

    Optional<DailyAssessment> findFirstByElderlyIdOrderByAssessmentDateAsc(Long elderlyId);

    Optional<DailyAssessment> findFirstByElderlyIdAndLevelNotOrderByAssessmentDateDesc(Long elderlyId,
                                                                                       AssessmentLevel level);

    Optional<DailyAssessment> findFirstByElderlyIdAndAssessmentDateGreaterThanOrderByAssessmentDateAsc(
            Long elderlyId, LocalDate assessmentDate);

    /** 지표별 판정(daily_assessment_metric)도 함께 지워지도록 벌크 삭제가 아닌 엔티티 단위로 지운다. */
    void deleteByElderlyId(Long elderlyId);
}
