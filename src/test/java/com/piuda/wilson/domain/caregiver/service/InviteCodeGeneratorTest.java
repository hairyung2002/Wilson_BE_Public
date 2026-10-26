package com.piuda.wilson.domain.caregiver.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

class InviteCodeGeneratorTest {

    private final InviteCodeGenerator inviteCodeGenerator = new InviteCodeGenerator();

    @RepeatedTest(100)
    @DisplayName("헷갈리는 문자(0·O·1·I)를 뺀 영문 대문자·숫자 8자리 코드를 생성한다")
    void generate_eightCharacters() {
        // when
        String code = inviteCodeGenerator.generate();

        // then
        assertThat(code).matches("[A-HJ-NP-Z2-9]{8}");
    }
}
