package com.piuda.wilson.domain.elderly.dto;

import com.piuda.wilson.domain.elderly.entity.ConsentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 등록·가입 때 함께 받는 동의 한 항목. */
public record ConsentItem(

        @NotNull(message = "동의 종류는 필수입니다.")
        ConsentType type,

        @NotNull(message = "동의 여부는 필수입니다.")
        Boolean agreed,

        @NotNull(message = "약관 버전은 필수입니다.")
        @Positive(message = "약관 버전은 1 이상이어야 합니다.")
        Integer version
) {
}
