package com.piuda.wilson.domain.caregiver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 대소문자는 구분하지 않는다 (서비스에서 대문자로 맞춘다). */
public record JoinCareRelationshipRequest(

        @NotBlank(message = "초대 코드는 필수입니다.")
        @Pattern(regexp = "[A-Za-z0-9]{8}", message = "초대 코드는 영문·숫자 8자리입니다.")
        String inviteCode,

        @Size(max = 30, message = "관계는 30자 이하여야 합니다.")
        String relation
) {
}
