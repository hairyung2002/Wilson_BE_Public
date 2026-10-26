package com.piuda.wilson.domain.emergency.dto;

import com.piuda.wilson.domain.emergency.entity.Emergency;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import java.time.LocalDateTime;

/** 응급 목록 항목 (api-spec 15-3). acknowledgedBy는 처음 확인한 보호자 이름, 확인 전이면 null. */
public record EmergencyResponse(Long emergencyId, EmergencyCategory category, LocalDateTime detectedAt,
                                LocalDateTime acknowledgedAt, String acknowledgedBy) {

    public static EmergencyResponse from(Emergency emergency) {
        return new EmergencyResponse(
                emergency.getId(),
                emergency.getCategory(),
                emergency.getDetectedAt(),
                emergency.getAcknowledgedAt(),
                emergency.isAcknowledged() ? emergency.getAcknowledgedBy().getName() : null
        );
    }
}
