package com.piuda.wilson.domain.elderly.dto;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.entity.Gender;
import java.time.LocalDate;

public record ElderlyDetailResponse(Long id, String name, LocalDate birth, Gender gender, String relationship) {

    public static ElderlyDetailResponse from(CareRelationship careRelationship) {
        Elderly elderly = careRelationship.getElderly();
        return new ElderlyDetailResponse(
                elderly.getId(),
                elderly.getName(),
                elderly.getBirthDate(),
                elderly.getGender(),
                careRelationship.getRelation()
        );
    }
}
