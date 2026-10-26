package com.piuda.wilson.global.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    @DisplayName("데이터를 담아 성공 응답을 생성한다")
    void success_withData() {
        // given
        String data = "hello";

        // when
        ApiResponse<String> response = ApiResponse.success(data);

        // then
        assertThat(response.message()).isEqualTo("SUCCESS");
        assertThat(response.data()).isEqualTo("hello");
    }

    @Test
    @DisplayName("데이터 없이 성공 응답을 생성한다")
    void success_withoutData() {
        // when
        ApiResponse<Void> response = ApiResponse.success();

        // then
        assertThat(response.message()).isEqualTo("SUCCESS");
        assertThat(response.data()).isNull();
    }
}
