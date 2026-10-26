package com.piuda.wilson.domain.emergency.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.emergency.dto.EmergencyResponse;
import com.piuda.wilson.domain.emergency.service.EmergencyService;
import com.piuda.wilson.global.common.response.PageResponse;
import com.piuda.wilson.global.config.SecurityConfig;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.global.security.JwtTokenProvider;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(EmergencyController.class)
@Import(SecurityConfig.class)
class EmergencyControllerTest {

    private static final Long MEMBER_ID = 5L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmergencyService emergencyService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    private RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of()));
    }

    @Test
    @DisplayName("응급 목록을 페이지 형식으로 돌려준다")
    void getEmergencies() throws Exception {
        // given
        given(emergencyService.getEmergencies(MEMBER_ID, 15L, PageRequest.of(0, 20))).willReturn(new PageResponse<>(
                0, 20, 1, 1, List.of(new EmergencyResponse(7L, EmergencyCategory.FALL,
                LocalDateTime.of(2026, 10, 20, 7, 59), null, null))));

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/emergencies").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].emergencyId").value(7))
                .andExpect(jsonPath("$.data.content[0].category").value("FALL"))
                .andExpect(jsonPath("$.data.content[0].detectedAt").value("2026-10-20T07:59:00"))
                .andExpect(jsonPath("$.data.content[0].acknowledgedAt").isEmpty());
    }

    @Test
    @DisplayName("페이지 크기가 100을 넘으면 400")
    void getEmergencies_sizeTooLarge() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/emergencies").with(loginMember()).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    @DisplayName("확인하면 204")
    void acknowledge() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/emergencies/7/acknowledge").with(loginMember()))
                .andExpect(status().isNoContent());
        verify(emergencyService).acknowledge(MEMBER_ID, 7L);
    }

    @Test
    @DisplayName("없는 응급 기록을 확인하면 404 EM001")
    void acknowledge_notFound() throws Exception {
        // given
        willThrow(new CustomException(ErrorCode.EMERGENCY_NOT_FOUND)).given(emergencyService).acknowledge(MEMBER_ID, 7L);

        // when & then
        mockMvc.perform(patch("/api/v1/emergencies/7/acknowledge").with(loginMember()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EM001"));
    }
}
