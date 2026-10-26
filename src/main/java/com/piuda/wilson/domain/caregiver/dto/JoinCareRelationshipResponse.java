package com.piuda.wilson.domain.caregiver.dto;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;

public record JoinCareRelationshipResponse(Long careRelationshipId, Long elderlyId, CareRole role) {

    public static JoinCareRelationshipResponse from(CareRelationship careRelationship) {
        return new JoinCareRelationshipResponse(careRelationship.getId(), careRelationship.getElderly().getId(),
                careRelationship.getRole());
    }
}
