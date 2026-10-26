package com.piuda.wilson.domain.analysis.repository;

import com.piuda.wilson.domain.analysis.entity.ElderBaseline;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElderBaselineRepository extends JpaRepository<ElderBaseline, Long> {

    List<ElderBaseline> findByElderlyId(Long elderlyId);

    void deleteByElderlyId(Long elderlyId);
}
