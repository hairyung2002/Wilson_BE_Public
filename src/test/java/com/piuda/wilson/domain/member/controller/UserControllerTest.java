package com.piuda.wilson.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.member.dto.MemberResponse;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.member.service.UserService;
import com.piuda.wilson.global.config.SecurityConfig;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    private static final Long MEMBER_ID = 5L;
    private static final MemberResponse ME = new MemberResponse(MEMBER_ID, "홍길동", "care@wilson.com",
            "09012345678", MemberType.CAREGIVER, LocalDateTime.of(2026, 10, 1, 9, 0), null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    private RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of()));
    }

    @Test
    @DisplayName("내 정보를 반환한다")
    void getMe() throws Exception {
        // given
        given(userService.getMe(MEMBER_ID)).willReturn(ME);

        // when & then
        mockMvc.perform(get("/api/v1/users/me").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("care@wilson.com"))
                .andExpect(jsonPath("$.data.phone").value("09012345678"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-01T09:00:00"));
    }

    @Test
    @DisplayName("JWT 없이 내 정보를 조회하면 401을 반환한다")
    void getMe_unauthorized() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회원정보를 수정하면 수정된 정보를 반환한다")
    void updateMe() throws Exception {
        // given
        given(userService.updateMe(eq(MEMBER_ID), any())).willReturn(ME);

        // when & then
        mockMvc.perform(patch("/api/v1/users/me")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","phone":"09012345678"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("홍길동"));
    }

    @Test
    @DisplayName("이름이 공백이면 400을 반환한다")
    void updateMe_blankName() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/users/me")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        verify(userService, never()).updateMe(any(), any());
    }

    @Test
    @DisplayName("전화번호가 숫자 10~11자리가 아니면 400을 반환한다")
    void updateMe_invalidPhone() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/users/me")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"090-1234-5678"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
    }

    @Test
    @DisplayName("비밀번호를 변경하면 204를 반환한다")
    void changePassword() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/users/password")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"current-pw","newPassword":"new-password"}
                                """))
                .andExpect(status().isNoContent());
        verify(userService).changePassword(eq(MEMBER_ID), any());
    }

    @Test
    @DisplayName("새 비밀번호가 8자 미만이면 400을 반환한다")
    void changePassword_tooShort() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/users/password")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"current-pw","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 400과 M003을 반환한다")
    void changePassword_mismatch() throws Exception {
        // given
        willThrow(new CustomException(ErrorCode.PASSWORD_MISMATCH))
                .given(userService).changePassword(eq(MEMBER_ID), any());

        // when & then
        mockMvc.perform(patch("/api/v1/users/password")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrong-pw","newPassword":"new-password"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("M003"));
    }

    @Test
    @DisplayName("탈퇴하면 204를 반환한다")
    void withdraw() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/users/me").with(loginMember()))
                .andExpect(status().isNoContent());
        verify(userService).withdraw(MEMBER_ID);
    }

    @Test
    @DisplayName("노인 회원이 탈퇴하려 하면 403을 반환한다")
    void withdraw_elderly() throws Exception {
        // given
        willThrow(new CustomException(ErrorCode.NO_PERMISSION)).given(userService).withdraw(MEMBER_ID);

        // when & then
        mockMvc.perform(delete("/api/v1/users/me").with(loginMember()))
                .andExpect(status().isForbidden());
    }
}
