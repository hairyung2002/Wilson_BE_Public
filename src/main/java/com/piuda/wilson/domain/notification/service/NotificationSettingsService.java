package com.piuda.wilson.domain.notification.service;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.notification.dto.NotificationSettingsRequest;
import com.piuda.wilson.domain.notification.dto.NotificationSettingsResponse;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 노인별로 요청한 보호자 본인의 알림 설정 (api-spec 16-1). */
@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final CaregiverRepository caregiverRepository;
    private final CareRelationshipRepository careRelationshipRepository;

    @Transactional(readOnly = true)
    public NotificationSettingsResponse get(Long memberId, Long elderlyId) {
        return NotificationSettingsResponse.from(findMine(memberId, elderlyId));
    }

    @Transactional
    public NotificationSettingsResponse update(Long memberId, Long elderlyId, NotificationSettingsRequest request) {
        CareRelationship mine = findMine(memberId, elderlyId);
        mine.updateNotificationSettings(request.patternChange(), request.proactiveNoResponse(),
                request.dailySummary());
        return NotificationSettingsResponse.from(mine);
    }

    private CareRelationship findMine(Long memberId, Long elderlyId) {
        Caregiver caregiver = caregiverRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAREGIVER_NOT_FOUND));
        return careRelationshipRepository.findByCaregiverIdAndElderlyId(caregiver.getId(), elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.NO_PERMISSION));
    }
}
