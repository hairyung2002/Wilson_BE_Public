package com.piuda.wilson.domain.elderly.repository;

import com.piuda.wilson.domain.elderly.entity.Consent;
import com.piuda.wilson.domain.elderly.entity.ConsentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    Optional<Consent> findFirstByElderlyIdAndTypeOrderByIdDesc(Long elderlyId, ConsentType type);

    List<Consent> findByElderlyIdOrderByIdDesc(Long elderlyId);

    void deleteByElderlyId(Long elderlyId);
}
