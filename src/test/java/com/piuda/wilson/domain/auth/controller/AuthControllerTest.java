package com.piuda.wilson.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.auth.dto.LoginResponse;
import com.piuda.wilson.domain.auth.dto.ReissueResponse;
import com.piuda.wilson.domain.auth.service.AuthService;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.global.config.SecurityConfig;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.global.security.JwtTokenProvider;
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

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    @Test
    @DisplayName("로그인 성공 시 200과 액세스/리프레시 토큰을 반환한다")
    void login_success() throws Exception {
        // given
        String requestBody = """
                {"email":"user@wilson.com","password":"raw-password"}
                """;
        given(authService.login(any()))
                .willReturn(LoginResponse.of("access-token", "refresh-token", 7L, MemberType.CAREGIVER));

        // when & then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("자격 증명이 틀리면 401을 반환한다")
    void login_invalidCredentials() throws Exception {
        // given
        String requestBody = """
                {"email":"user@wilson.com","password":"wrong-password"}
                """;
        willThrow(new CustomException(ErrorCode.INVALID_CREDENTIALS))
                .given(authService).login(any());

        // when & then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("리프레시 토큰 재발급 성공 시 200과 새 토큰을 반환한다")
    void reissue_success() throws Exception {
        // given
        String requestBody = """
                {"refreshToken":"valid-refresh"}
                """;
        given(authService.reissue(any()))
                .willReturn(ReissueResponse.of("new-access", "new-refresh"));

        // when & then
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
    }

    @Test
    @DisplayName("유효하지 않은 리프레시 토큰이면 401을 반환한다")
    void reissue_invalidToken() throws Exception {
        // given
        String requestBody = """
                {"refreshToken":"invalid-refresh"}
                """;
        willThrow(new CustomException(ErrorCode.INVALID_REFRESH_TOKEN))
                .given(authService).reissue(any());

        // when & then
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    @DisplayName("기기 연결 코드로 토큰 없이 로그인하면 200과 토큰을 반환한다")
    void deviceLogin_success() throws Exception {
        // given
        given(authService.deviceLogin(any(), any()))
                .willReturn(LoginResponse.of("access-token", "refresh-token", 9L, MemberType.ELDERLY));

        // when & then
        mockMvc.perform(post("/api/v1/auth/device-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode":"012345"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.memberType").value("ELDERLY"));
    }

    @Test
    @DisplayName("기기 연결 코드가 6자리 숫자가 아니면 400을 반환한다")
    void deviceLogin_malformedCode_badRequest() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/auth/device-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode":"12ab"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("deviceCode"));
    }

    @Test
    @DisplayName("유효하지 않은 기기 연결 코드면 401을 반환한다")
    void deviceLogin_invalidCode_unauthorized() throws Exception {
        // given
        willThrow(new CustomException(ErrorCode.INVALID_DEVICE_CODE)).given(authService).deviceLogin(any(), any());

        // when & then
        mockMvc.perform(post("/api/v1/auth/device-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode":"000000"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A003"));
    }

    @Test
    @DisplayName("요청한 기기의 IP로 시도 횟수를 세고, 초과하면 429를 반환한다")
    void deviceLogin_tooManyAttempts() throws Exception {
        // given
        willThrow(new CustomException(ErrorCode.TOO_MANY_DEVICE_LOGIN_ATTEMPTS))
                .given(authService).deviceLogin(any(), eq("10.0.0.7"));

        // when & then
        mockMvc.perform(post("/api/v1/auth/device-login")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.7");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode":"000000"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("A004"));
    }

    @Test
    @DisplayName("로그아웃하면 204를 반환한다")
    void logout_success() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(authentication(new UsernamePasswordAuthenticationToken(5L, null, List.of()))))
                .andExpect(status().isNoContent());
        verify(authService).logout(5L);
    }

    @Test
    @DisplayName("인증 없이 로그아웃하면 401을 반환한다")
    void logout_unauthorized() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
}
