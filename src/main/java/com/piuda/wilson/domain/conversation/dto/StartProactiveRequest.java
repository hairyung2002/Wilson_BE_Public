package com.piuda.wilson.domain.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 노인 기기가 선제 대화 푸시로 받은 트리거 ID (api-spec 6-2). */
public record StartProactiveRequest(

        @NotBlank(message = "트리거 ID는 필수입니다.")
        @Size(max = 36, message = "트리거 ID 형식이 올바르지 않습니다.")
        String triggerId
) {
}
