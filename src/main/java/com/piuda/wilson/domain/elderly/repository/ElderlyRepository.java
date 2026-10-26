package com.piuda.wilson.domain.elderly.repository;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ElderlyRepository extends JpaRepository<Elderly, Long> {

    @Query("select e.id from Elderly e where e.orphanedAt < :cutoff and e.deleted = false")
    List<Long> findOrphanedIdsBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("select e.id from Elderly e where e.deleted = false")
    List<Long> findActiveIds();

    Optional<Elderly> findByMemberId(Long memberId);

    Optional<Elderly> findByIdAndDeletedFalse(Long id);

    Optional<Elderly> findByMemberIdAndDeletedFalse(Long memberId);
}
