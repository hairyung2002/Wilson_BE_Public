package com.piuda.wilson.domain.caregiver.service;

import com.piuda.wilson.domain.caregiver.dto.CaregiverResponse;
import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 노인의 보호자 목록·주보호자 넘기기·연결 해제 (api-spec 5-9~5-11). */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareRelationshipService {

    private final CaregiverRepository caregiverRepository;
    private final ElderlyRepository elderlyRepository;
    private final CareRelationshipRepository careRelationshipRepository;

    @Transactional(readOnly = true)
    public List<CaregiverResponse> getCaregivers(Long memberId, Long elderlyId) {
        elderlyRepository.findByIdAndDeletedFalse(elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ELDERLY_NOT_FOUND));
        Caregiver requester = findCaregiver(memberId);
        findLinked(requester, elderlyId);
        return careRelationshipRepository.findAllWithCaregiverByElderlyId(elderlyId).stream()
                .map(careRelationship -> CaregiverResponse.of(careRelationship, requester))
                .toList();
    }

    /**
     * 노인당 OWNER는 1명이라(DB 유니크 인덱스) 기존 OWNER를 먼저 내려 DB에 반영한 뒤 새 OWNER를 올린다.
     */
    @Transactional
    public void transferOwner(Long memberId, Long elderlyId, Long targetRelationshipId) {
        CareRelationship mine = findLinked(findCaregiver(memberId), elderlyId);
        if (!mine.isOwner()) {
            throw new CustomException(ErrorCode.NO_PERMISSION);
        }
        CareRelationship target = careRelationshipRepository.findById(targetRelationshipId)
                .filter(relationship -> relationship.getElderly().getId().equals(elderlyId))
                .filter(relationship -> !relationship.isOwner())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_OWNER_TRANSFER_TARGET));

        mine.demoteToMember();
        careRelationshipRepository.flush();
        target.promoteToOwner();
        log.info("Care owner transferred: elderlyId={}, from={}, to={}", elderlyId, mine.getId(), target.getId());
    }

    /** MEMBER는 스스로 나가고, OWNER는 MEMBER를 내보낸다. OWNER 자신은 먼저 주보호자를 넘겨야 한다. */
    @Transactional
    public void unlink(Long memberId, Long careRelationshipId) {
        Caregiver requester = findCaregiver(memberId);
        CareRelationship target = careRelationshipRepository.findById(careRelationshipId)
                .orElseThrow(() -> new CustomException(ErrorCode.CARE_RELATIONSHIP_NOT_FOUND));

        if (target.isOf(requester)) {
            if (target.isOwner()) {
                throw new CustomException(ErrorCode.OWNER_TRANSFER_REQUIRED);
            }
        } else if (!findLinked(requester, target.getElderly().getId()).isOwner()) {
            throw new CustomException(ErrorCode.NO_PERMISSION);
        }

        careRelationshipRepository.delete(target);
        log.info("Care relationship removed: careRelationshipId={}, elderlyId={}, by caregiverId={}",
                careRelationshipId, target.getElderly().getId(), requester.getId());
    }

    private Caregiver findCaregiver(Long memberId) {
        return caregiverRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAREGIVER_NOT_FOUND));
    }

    private CareRelationship findLinked(Caregiver caregiver, Long elderlyId) {
        return careRelationshipRepository.findByCaregiverIdAndElderlyId(caregiver.getId(), elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.NO_PERMISSION));
    }
}
