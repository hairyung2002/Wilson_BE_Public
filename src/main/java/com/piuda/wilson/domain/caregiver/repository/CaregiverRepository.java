package com.piuda.wilson.domain.caregiver.repository;

import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaregiverRepository extends JpaRepository<Caregiver, Long> {

    Optional<Caregiver> findByMemberId(Long memberId);
}
