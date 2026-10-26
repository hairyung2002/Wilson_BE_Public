package com.piuda.wilson.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberSignUpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("회원가입 요청이 컨트롤러부터 DB까지 흘러 암호화된 비밀번호로 저장된다")
    void signUp_persistsEncodedPassword() throws Exception {
        // given
        String requestBody = """
                {"email":"integration@wilson.com","password":"raw-password","name":"통합회원","memberType":"CAREGIVER"}
                """;

        // when
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("integration@wilson.com"));

        // then
        Member saved = memberRepository.findByEmail("integration@wilson.com").orElseThrow();
        assertThat(saved.getName()).isEqualTo("통합회원");
        assertThat(saved.getPassword()).isNotEqualTo("raw-password");
        assertThat(saved.getPassword()).startsWith("$2");
    }
}
