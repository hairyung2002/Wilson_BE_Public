package com.piuda.wilson.domain.notification.dto;

import com.piuda.wilson.domain.notification.entity.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterPushTokenRequest(

        @NotBlank(message = "푸시 토큰은 필수입니다.")
        @Size(max = 512, message = "푸시 토큰은 512자 이하여야 합니다.")
        String token,

        @NotNull(message = "플랫폼은 필수입니다.")
        PushPlatform platform
) {
}
