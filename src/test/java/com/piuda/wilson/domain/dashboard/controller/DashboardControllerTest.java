package com.piuda.wilson.domain.dashboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.analysis.entity.AssessmentLevel;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.conversation.dto.ConversationSummaryResponse;
import com.piuda.wilson.domain.conversation.entity.ConversationOrigin;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.dashboard.dto.DashboardPeriod;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.AssessmentStatusResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.BaselineResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.PointResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.TodayResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.TrendResponse;
import com.piuda.wilson.domain.dashboard.service.DashboardService;
import com.piuda.wilson.global.config.SecurityConfig;
import com.piuda.wilson.global.security.JwtTokenProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    private static final Long MEMBER_ID = 5L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    private RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of()));
    }

    private static DashboardResponse response() {
        return new DashboardResponse(new TodayResponse(3, 12, 2, 0),
                new AssessmentStatusResponse(AssessmentLevel.LOW, AssessmentLevel.LOW.message(), LocalDate.of(2026, 10, 18)),
                List.of(new ConversationSummaryResponse(15L, ConversationType.MEAL, ConversationOrigin.PROACTIVE,
                        ConversationState.FINISHED, LocalDateTime.of(2026, 10, 20, 8, 0), null, null)),
                List.of(new TrendResponse("subsequent_onset_latency_ms", "대답까지 걸리는 시간", "ms",
                        new BaselineResponse(820.0, 110.0), List.of(new PointResponse(LocalDate.of(2026, 10, 14), 790.0)))));
    }

    @Test
    @DisplayName("기간을 생략하면 WEEK로 대시보드를 돌려준다")
    void defaultWeek() throws Exception {
        // given
        given(dashboardService.getDashboard(MEMBER_ID, 15L, DashboardPeriod.WEEK)).willReturn(response());

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/dashboard").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today.conversationCount").value(3))
                .andExpect(jsonPath("$.data.today.totalMinutes").value(12))
                .andExpect(jsonPath("$.data.assessment.level").value("LOW"))
                .andExpect(jsonPath("$.data.assessment.since").value("2026-10-18"))
                .andExpect(jsonPath("$.data.recentConversations[0].origin").value("PROACTIVE"))
                .andExpect(jsonPath("$.data.trends[0].baseline.mean").value(820.0))
                .andExpect(jsonPath("$.data.trends[0].points[0].date").value("2026-10-14"));
    }

    @Test
    @DisplayName("MONTH로 조회할 수 있고, 정의되지 않은 기간이면 400")
    void period() throws Exception {
        // given
        given(dashboardService.getDashboard(MEMBER_ID, 15L, DashboardPeriod.MONTH)).willReturn(response());

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/dashboard").with(loginMember()).param("period", "MONTH"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/elderlies/15/dashboard").with(loginMember()).param("period", "YEAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
        verify(dashboardService).getDashboard(any(), any(), any());
    }

    @Test
    @DisplayName("인증되지 않으면 401")
    void unauthorized() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/dashboard"))
                .andExpect(status().isUnauthorized());
        verify(dashboardService, never()).getDashboard(any(), any(), any());
    }
}
