package com.piuda.wilson.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomExceptionTest {

    @Test
    @DisplayName("CustomException은 ErrorCode를 보유하고 메시지를 전달한다")
    void holdsErrorCode() {
        // given
        ErrorCode errorCode = ErrorCode.ENTITY_NOT_FOUND;

        // when
        CustomException exception = new CustomException(errorCode);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getMessage()).isEqualTo(errorCode.getMessage());
    }
}
