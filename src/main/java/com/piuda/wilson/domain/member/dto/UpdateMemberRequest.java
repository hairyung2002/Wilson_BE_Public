package com.piuda.wilson.domain.member.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 부분 수정 요청. null인 항목은 변경하지 않는다. 이메일은 변경할 수 없다. */
public record UpdateMemberRequest(

        @Pattern(regexp = ".*\\S.*", message = "이름은 공백일 수 없습니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @Pattern(regexp = "\\d{10,11}", message = "전화번호는 숫자 10~11자리여야 합니다.")
        String phone
) {
}
