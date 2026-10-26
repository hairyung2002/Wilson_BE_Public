package com.piuda.wilson.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.member.dto.CreateMemberResponse;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.member.service.MemberService;
import com.piuda.wilson.global.config.SecurityConfig;
import com.piuda.wilson.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    @Test
    @DisplayName("유효한 요청이면 201과 생성된 회원 정보를 반환한다")
    void signUp_success() throws Exception {
        // given
        String requestBody = """
                {"email":"new@wilson.com","password":"raw-password","name":"김윌슨","memberType":"ELDERLY"}
                """;
        given(memberService.create(any()))
                .willReturn(new CreateMemberResponse(1L, "new@wilson.com", "김윌슨", MemberType.ELDERLY));

        // when & then
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("new@wilson.com"))
                .andExpect(jsonPath("$.data.memberType").value("ELDERLY"));
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 400을 반환한다")
    void signUp_invalidEmail_badRequest() throws Exception {
        // given
        String requestBody = """
                {"email":"not-an-email","password":"raw-password","name":"김윌슨","memberType":"ELDERLY"}
                """;

        // when & then
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }
}
