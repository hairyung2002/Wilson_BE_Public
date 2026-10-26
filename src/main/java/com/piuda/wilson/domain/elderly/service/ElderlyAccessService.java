package com.piuda.wilson.domain.elderly.service;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노인 데이터 접근 권한 확인 (api-spec 5장 권한 표). 보호자와 노인 본인이 함께 쓰는 기능(일정·설정)에 쓴다.
 *
 * <ul>
 *   <li>조회(viewer): 연결된 보호자(OWNER·MEMBER), 노인 본인</li>
 *   <li>관리(manager): OWNER, 노인 본인</li>
 *   <li>보호자 전용(caregiver): 연결된 보호자(OWNER·MEMBER)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ElderlyAccessService {

    private final ElderlyRepository elderlyRepository;
    private final CaregiverRepository caregiverRepository;
    private final CareRelationshipRepository careRelationshipRepository;

    @Transactional(readOnly = true)
    public ElderlyAccess requireViewer(Long memberId, Long elderlyId) {
        Elderly elderly = elderlyRepository.findByIdAndDeletedFalse(elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ELDERLY_NOT_FOUND));
        if (elderly.getMember() != null && elderly.getMember().getId().equals(memberId)) {
            return new ElderlyAccess(elderly, AccessActor.SELF);
        }
        CareRelationship relationship = findRelationship(memberId, elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.NO_PERMISSION));
        return new ElderlyAccess(elderly, relationship.isOwner() ? AccessActor.OWNER : AccessActor.MEMBER);
    }

    @Transactional(readOnly = true)
    public ElderlyAccess requireManager(Long memberId, Long elderlyId) {
        ElderlyAccess access = requireViewer(memberId, elderlyId);
        if (access.actor() == AccessActor.MEMBER) {
            throw new CustomException(ErrorCode.NO_PERMISSION);
        }
        return access;
    }

    /** 판정 이력·대시보드처럼 보호자에게만 보여 주는 화면. 노인 본인도 볼 수 없다. */
    @Transactional(readOnly = true)
    public ElderlyAccess requireCaregiver(Long memberId, Long elderlyId) {
        ElderlyAccess access = requireViewer(memberId, elderlyId);
        if (access.isSelf()) {
            throw new CustomException(ErrorCode.NO_PERMISSION);
        }
        return access;
    }

    private Optional<CareRelationship> findRelationship(Long memberId, Long elderlyId) {
        return caregiverRepository.findByMemberId(memberId)
                .flatMap(caregiver -> careRelationshipRepository.findByCaregiverIdAndElderlyId(caregiver.getId(),
                        elderlyId));
    }
}
