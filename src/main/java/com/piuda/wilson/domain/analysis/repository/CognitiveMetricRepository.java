package com.piuda.wilson.domain.analysis.repository;

import com.piuda.wilson.domain.analysis.entity.CognitiveMetric;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CognitiveMetricRepository extends JpaRepository<CognitiveMetric, Long> {

    boolean existsByTurnId(String turnId);

    /** 기준값 계산용. 기준값이 모두 생기기 전(처음 2주 남짓)에만 쓴다. */
    List<CognitiveMetric> findBySubjectIdAndAnalyzableTrueAndAnalyzedAtLessThan(UUID subjectId, Instant before);

    List<CognitiveMetric> findBySubjectIdAndAnalyzableTrueAndAnalyzedAtGreaterThanEqualAndAnalyzedAtLessThan(
            UUID subjectId, Instant from, Instant to);

    void deleteBySubjectId(UUID subjectId);
}
