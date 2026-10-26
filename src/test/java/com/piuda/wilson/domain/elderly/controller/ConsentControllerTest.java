package com.piuda.wilson.domain.elderly.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.elderly.dto.ConsentResponse;
import com.piuda.wilson.domain.elderly.dto.UpdateConsentRequest;
import com.piuda.wilson.domain.elderly.entity.ConsentGrantor;
import com.piuda.wilson.domain.elderly.entity.ConsentType;
import com.piuda.wilson.domain.elderly.entity.LegalBasis;
import com.piuda.wilson.domain.elderly.service.ConsentService;
import com.piuda.wilson.global.config.SecurityConfig;
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

@WebMvcTest(ConsentController.class)
@Import(SecurityConfig.class)
class ConsentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsentService consentService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    private static RequestPostProcessor member(Long memberId) {
        return authentication(new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
    }

    @Test
    @DisplayName("종류별 최신 동의를 반환한다")
    void getConsents() throws Exception {
        // given
        given(consentService.getConsents(5L, 15L)).willReturn(List.of(new ConsentResponse(
                ConsentType.SENSITIVE_INFO, true, 1, ConsentGrantor.OWNER, LegalBasis.FAMILY_DELEGATION,
                LocalDateTime.of(2026, 10, 20, 9, 0))));

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/consents").with(member(5L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("SENSITIVE_INFO"))
                .andExpect(jsonPath("$.data[0].grantedBy").value("OWNER"))
                .andExpect(jsonPath("$.data[0].legalBasis").value("FAMILY_DELEGATION"));
    }

    @Test
    @DisplayName("동의를 바꾸면 204")
    void updateConsent() throws Exception {
        // when & then
        mockMvc.perform(put("/api/v1/elderlies/15/consents/TRANSCRIPT_SHARING").with(member(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agreed":true,"version":1,"legalBasis":"FAMILY_DELEGATION"}
                                """))
                .andExpect(status().isNoContent());
        verify(consentService).updateConsent(5L, 15L, ConsentType.TRANSCRIPT_SHARING,
                new UpdateConsentRequest(true, 1, LegalBasis.FAMILY_DELEGATION));
    }

    @Test
    @DisplayName("없는 동의 종류나 동의 여부 누락은 400")
    void updateConsent_invalid() throws Exception {
        // when & then
        mockMvc.perform(put("/api/v1/elderlies/15/consents/UNKNOWN").with(member(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agreed":true,"version":1}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/elderlies/15/consents/TRANSCRIPT_SHARING").with(member(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("agreed"));
        verify(consentService, never()).updateConsent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("인증 없이는 401")
    void unauthorized() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/consents")).andExpect(status().isUnauthorized());
        verify(consentService, never()).getConsents(eq(5L), any());
    }
}
