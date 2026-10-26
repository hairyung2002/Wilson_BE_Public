package com.piuda.wilson.domain.elderly.dto;

import com.piuda.wilson.domain.elderly.entity.Gender;
import com.piuda.wilson.domain.elderly.entity.LegalBasis;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CreateElderlyRequest(

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @NotNull(message = "생년월일은 필수입니다.")
        @Past(message = "생년월일은 오늘 이전이어야 합니다.")
        LocalDate birth,

        @NotNull(message = "성별은 필수입니다.")
        Gender gender,

        @NotBlank(message = "관계는 필수입니다.")
        @Size(max = 30, message = "관계는 30자 이하여야 합니다.")
        String relationship,

        /** 민감정보 처리 동의(SENSITIVE_INFO)는 필수다. 없으면 400 CS001 (api-spec 5-12). */
        List<@Valid ConsentItem> consents,

        /** 대리 동의의 법적 근거. SELF는 허용하지 않는다. */
        LegalBasis legalBasis
) {
}
