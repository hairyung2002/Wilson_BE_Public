package com.piuda.wilson.domain.caregiver.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.caregiver.dto.CaregiverResponse;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.service.CareRelationshipService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CareRelationshipController.class)
@Import(SecurityConfig.class)
class CareRelationshipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CareRelationshipService careRelationshipService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    private static RequestPostProcessor member(Long memberId) {
        return authentication(new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
    }

    @Test
    @DisplayName("노인의 보호자 목록을 반환한다")
    void getCaregivers() throws Exception {
        // given
        given(careRelationshipService.getCaregivers(5L, 15L)).willReturn(List.of(
                new CaregiverResponse(30L, "김보호", "아들", CareRole.OWNER, true),
                new CaregiverResponse(31L, "김딸", "딸", CareRole.MEMBER, false)));

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/caregivers").with(member(5L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[0].me").value(true))
                .andExpect(jsonPath("$.data[1].name").value("김딸"));
    }

    @Test
    @DisplayName("주보호자를 넘기면 204")
    void transferOwner() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/elderlies/15/owner").with(member(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"careRelationshipId":31}
                                """))
                .andExpect(status().isNoContent());
        verify(careRelationshipService).transferOwner(5L, 15L, 31L);
    }

    @Test
    @DisplayName("넘길 대상이 없으면 400")
    void transferOwner_missingTarget() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/elderlies/15/owner").with(member(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("careRelationshipId"));
    }

    @Test
    @DisplayName("연결을 해제하면 204, 주보호자 본인이면 409")
    void unlink() throws Exception {
        // given
        willThrow(new CustomException(ErrorCode.OWNER_TRANSFER_REQUIRED))
                .given(careRelationshipService).unlink(5L, 30L);

        // when & then
        mockMvc.perform(delete("/api/v1/care-relationships/31").with(member(5L)))
                .andExpect(status().isNoContent());
        verify(careRelationshipService).unlink(5L, 31L);
        mockMvc.perform(delete("/api/v1/care-relationships/30").with(member(5L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("G005"));
    }

    @Test
    @DisplayName("인증 없이는 401")
    void unauthorized() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/caregivers"))
                .andExpect(status().isUnauthorized());
    }
}
