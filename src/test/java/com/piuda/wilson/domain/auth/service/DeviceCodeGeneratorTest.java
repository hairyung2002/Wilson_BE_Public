package com.piuda.wilson.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

class DeviceCodeGeneratorTest {

    private final DeviceCodeGenerator deviceCodeGenerator = new DeviceCodeGenerator();

    @RepeatedTest(100)
    @DisplayName("앞자리 0을 포함한 6자리 숫자 코드를 생성한다")
    void generate_sixDigits() {
        // when
        String code = deviceCodeGenerator.generate();

        // then
        assertThat(code).matches("\\d{6}");
    }
}
