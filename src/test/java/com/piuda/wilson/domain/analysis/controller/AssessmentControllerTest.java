package com.piuda.wilson.domain.analysis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.analysis.dto.AssessmentResponse;
import com.piuda.wilson.domain.analysis.dto.AssessmentResponse.FlaggedMetricResponse;
import com.piuda.wilson.domain.analysis.entity.AssessmentLevel;
import com.piuda.wilson.domain.analysis.service.AssessmentQueryService;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.global.config.SecurityConfig;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.global.security.JwtTokenProvider;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AssessmentController.class)
@Import(SecurityConfig.class)
class AssessmentControllerTest {

    private static final Long MEMBER_ID = 5L;
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 20);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssessmentQueryService assessmentQueryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    private RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of()));
    }

    @Test
    @DisplayName("판정 이력을 날짜·위험도·문구·판정된 지표로 돌려준다")
    void getAssessments() throws Exception {
        // given
        given(assessmentQueryService.getAssessments(MEMBER_ID, 15L, FROM, TO)).willReturn(List.of(
                new AssessmentResponse(LocalDate.of(2026, 10, 19), AssessmentLevel.MEDIUM,
                        AssessmentLevel.MEDIUM.message(),
                        List.of(new FlaggedMetricResponse("subsequent_onset_latency_ms", "대답까지 걸리는 시간")))));

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/assessments").with(loginMember())
                        .param("from", "2026-10-01").param("to", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.data[0].level").value("MEDIUM"))
                .andExpect(jsonPath("$.data[0].message").value("최근 며칠 평소와 조금 다른 대화 모습이 보여요"))
                .andExpect(jsonPath("$.data[0].flaggedMetrics[0].metric").value("subsequent_onset_latency_ms"))
                .andExpect(jsonPath("$.data[0].flaggedMetrics[0].label").value("대답까지 걸리는 시간"));
    }

    @Test
    @DisplayName("기간 없이 조회하면 400")
    void missingRange() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/assessments").with(loginMember()).param("from", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
        verify(assessmentQueryService, never()).getAssessments(any(), any(), any(), any());
    }

    @Test
    @DisplayName("노인 본인이나 연결되지 않은 보호자가 조회하면 403")
    void forbidden() throws Exception {
        // given
        given(assessmentQueryService.getAssessments(MEMBER_ID, 15L, FROM, TO))
                .willThrow(new CustomException(ErrorCode.NO_PERMISSION));

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/assessments").with(loginMember())
                        .param("from", "2026-10-01").param("to", "2026-10-20"))
                .andExpect(status().isForbidden());
    }
}
