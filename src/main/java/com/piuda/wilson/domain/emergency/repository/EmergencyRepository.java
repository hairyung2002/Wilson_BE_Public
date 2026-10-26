package com.piuda.wilson.domain.emergency.repository;

import com.piuda.wilson.domain.emergency.entity.Emergency;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyRepository extends JpaRepository<Emergency, Long> {

    /** 같은 노인·같은 종류로 이미 보호자에게 알린 응급이 기준 시각 이후에 있는지 (10분 중복 알림 방지). */
    boolean existsByElderlyIdAndCategoryAndNotifiedTrueAndDetectedAtGreaterThanEqual(
            Long elderlyId, EmergencyCategory category, LocalDateTime since);

    @EntityGraph(attributePaths = "acknowledgedBy")
    Page<Emergency> findByElderlyIdOrderByDetectedAtDesc(Long elderlyId, Pageable pageable);

    void deleteByElderlyId(Long elderlyId);
}
