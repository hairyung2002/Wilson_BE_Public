package com.piuda.wilson.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.notification.dto.RegisterPushTokenRequest;
import com.piuda.wilson.domain.notification.entity.PushPlatform;
import com.piuda.wilson.domain.notification.service.PushTokenService;
import com.piuda.wilson.global.config.SecurityConfig;
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

@WebMvcTest(PushTokenController.class)
@Import(SecurityConfig.class)
class PushTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushTokenService pushTokenService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    @Test
    @DisplayName("푸시 토큰을 등록하면 204")
    void register() throws Exception {
        // when & then
        mockMvc.perform(put("/api/v1/users/me/push-token")
                        .with(authentication(new UsernamePasswordAuthenticationToken(5L, null, List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"fcm-token","platform":"ANDROID"}
                                """))
                .andExpect(status().isNoContent());
        verify(pushTokenService).register(5L, new RegisterPushTokenRequest("fcm-token", PushPlatform.ANDROID));
    }

    @Test
    @DisplayName("토큰이 비었거나 플랫폼이 없으면 400")
    void register_invalid() throws Exception {
        // when & then
        mockMvc.perform(put("/api/v1/users/me/push-token")
                        .with(authentication(new UsernamePasswordAuthenticationToken(5L, null, List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":" "}
                                """))
                .andExpect(status().isBadRequest());
        verify(pushTokenService, never()).register(any(), any());
    }
}
