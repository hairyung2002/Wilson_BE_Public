package com.piuda.wilson.domain.elderly.dto;

import com.piuda.wilson.domain.elderly.entity.LegalBasis;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** legalBasis는 주보호자의 대리 변경일 때 필수다. 노인 본인이면 무시하고 SELF로 저장한다. */
public record UpdateConsentRequest(

        @NotNull(message = "동의 여부는 필수입니다.")
        Boolean agreed,

        @NotNull(message = "약관 버전은 필수입니다.")
        @Positive(message = "약관 버전은 1 이상이어야 합니다.")
        Integer version,

        LegalBasis legalBasis
) {
}
