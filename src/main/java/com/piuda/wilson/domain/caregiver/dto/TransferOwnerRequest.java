package com.piuda.wilson.domain.caregiver.dto;

import jakarta.validation.constraints.NotNull;

public record TransferOwnerRequest(

        @NotNull(message = "주보호자를 넘길 보호자 연결은 필수입니다.")
        Long careRelationshipId
) {
}
