package com.piuda.wilson.domain.caregiver.repository;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareRelationshipRepository extends JpaRepository<CareRelationship, Long> {

    List<CareRelationship> findByCaregiverId(Long caregiverId);

    List<CareRelationship> findByElderlyId(Long elderlyId);

    boolean existsByCaregiverIdAndElderlyId(Long caregiverId, Long elderlyId);

    boolean existsByElderlyIdAndRole(Long elderlyId, CareRole role);

    long countByElderlyId(Long elderlyId);

    /** 보호자가 한 명 이상 연결된, 삭제되지 않은 노인 (일별 요약·판정 대상). */
    @Query("select distinct cr.elderly.id from CareRelationship cr where cr.elderly.deleted = false")
    List<Long> findActiveElderlyIds();

    @Query("""
            select cr from CareRelationship cr
            join fetch cr.caregiver c
            join fetch c.member
            where cr.elderly.id = :elderlyId
            order by cr.id
            """)
    List<CareRelationship> findAllWithCaregiverByElderlyId(@Param("elderlyId") Long elderlyId);

    void deleteByCaregiverId(Long caregiverId);

    void deleteByElderlyId(Long elderlyId);

    Optional<CareRelationship> findByCaregiverIdAndElderlyId(Long caregiverId, Long elderlyId);

    @Query("""
            select cr from CareRelationship cr
            join fetch cr.elderly e
            where cr.caregiver.id = :caregiverId and e.deleted = false
            order by cr.id
            """)
    List<CareRelationship> findAllWithActiveElderlyByCaregiverId(@Param("caregiverId") Long caregiverId);
}
