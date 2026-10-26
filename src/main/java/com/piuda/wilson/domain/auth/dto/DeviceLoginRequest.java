package com.piuda.wilson.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeviceLoginRequest(

        @NotBlank(message = "기기 연결 코드는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "기기 연결 코드는 6자리 숫자입니다.")
        String deviceCode
) {
}
