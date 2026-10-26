package com.piuda.wilson.domain.member.dto;

import com.piuda.wilson.domain.elderly.dto.ConsentItem;
import com.piuda.wilson.domain.member.entity.MemberType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateMemberRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String name,

        @NotNull(message = "회원 유형은 필수입니다.")
        MemberType memberType,

        /** 노인 본인 가입일 때만 받는다. 민감정보 처리 동의가 없으면 400 CS001. 보호자 가입에서는 무시한다. */
        List<@Valid ConsentItem> consents
) {
}
