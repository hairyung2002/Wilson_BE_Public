package com.piuda.wilson.domain.elderly.dto;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import java.time.LocalDate;

public record ElderlySummaryResponse(Long id, String name, Integer age, String relationship) {

    public static ElderlySummaryResponse of(CareRelationship careRelationship, LocalDate today) {
        Elderly elderly = careRelationship.getElderly();
        return new ElderlySummaryResponse(
                elderly.getId(),
                elderly.getName(),
                elderly.ageAt(today),
                careRelationship.getRelation()
        );
    }
}
