package com.piuda.wilson.domain.caregiver.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.caregiver.dto.InviteCodeResponse;
import com.piuda.wilson.domain.caregiver.dto.JoinCareRelationshipResponse;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.service.CareInvitationService;
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

@WebMvcTest(CareInvitationController.class)
@Import(SecurityConfig.class)
class CareInvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CareInvitationService careInvitationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    private static RequestPostProcessor member(Long memberId) {
        return authentication(new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
    }

    @Test
    @DisplayName("초대 코드를 발급하면 201과 코드·만료 시각을 반환한다")
    void issueInviteCode() throws Exception {
        // given
        given(careInvitationService.issueInviteCode(5L, 15L))
                .willReturn(new InviteCodeResponse("K7Q2M9XD", LocalDateTime.of(2026, 10, 21, 14, 0)));

        // when & then
        mockMvc.perform(post("/api/v1/elderlies/15/invite-code").with(member(5L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.inviteCode").value("K7Q2M9XD"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-10-21T14:00:00"));
    }

    @Test
    @DisplayName("초대 코드 발급 권한이 없으면 403")
    void issueInviteCode_forbidden() throws Exception {
        // given
        given(careInvitationService.issueInviteCode(5L, 15L)).willThrow(new CustomException(ErrorCode.NO_PERMISSION));

        // when & then
        mockMvc.perform(post("/api/v1/elderlies/15/invite-code").with(member(5L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C005"));
    }

    @Test
    @DisplayName("초대 코드로 연결하면 201과 역할을 반환한다")
    void join() throws Exception {
        // given
        given(careInvitationService.join(eq(5L), any()))
                .willReturn(new JoinCareRelationshipResponse(31L, 15L, CareRole.MEMBER));

        // when & then
        mockMvc.perform(post("/api/v1/care-relationships").with(member(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteCode":"K7Q2M9XD","relation":"딸"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.careRelationshipId").value(31))
                .andExpect(jsonPath("$.data.elderlyId").value(15))
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
    }

    @Test
    @DisplayName("초대 코드 없이 노인 ID만으로는 연결할 수 없다 (기존 방식 폐기)")
    void join_withoutInviteCode() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/care-relationships").with(member(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"elderlyId":15,"relation":"딸"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"))
                .andExpect(jsonPath("$.errors[0].field").value("inviteCode"));
        verify(careInvitationService, never()).join(any(), any());
    }

    @Test
    @DisplayName("초대 코드 형식이 다르면 400")
    void join_malformedCode() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/care-relationships").with(member(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteCode":"12-34","relation":"딸"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 연결하면 401")
    void join_unauthorized() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/care-relationships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteCode":"K7Q2M9XD","relation":"딸"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
