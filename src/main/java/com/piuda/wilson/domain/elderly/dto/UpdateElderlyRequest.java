package com.piuda.wilson.domain.elderly.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 부분 수정 요청. null인 항목은 변경하지 않는다. */
public record UpdateElderlyRequest(

        @Pattern(regexp = ".*\\S.*", message = "이름은 공백일 수 없습니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @Past(message = "생년월일은 오늘 이전이어야 합니다.")
        LocalDate birth,

        @Pattern(regexp = ".*\\S.*", message = "관계는 공백일 수 없습니다.")
        @Size(max = 30, message = "관계는 30자 이하여야 합니다.")
        String relationship
) {
}
