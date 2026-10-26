package com.piuda.wilson.domain.caregiver.dto;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;

/** 노인의 보호자 한 명. me는 요청한 보호자 본인인지 여부다. */
public record CaregiverResponse(Long careRelationshipId, String name, String relation, CareRole role, boolean me) {

    public static CaregiverResponse of(CareRelationship careRelationship, Caregiver requester) {
        return new CaregiverResponse(careRelationship.getId(), careRelationship.getCaregiver().getMember().getName(),
                careRelationship.getRelation(), careRelationship.getRole(), careRelationship.isOf(requester));
    }
}
