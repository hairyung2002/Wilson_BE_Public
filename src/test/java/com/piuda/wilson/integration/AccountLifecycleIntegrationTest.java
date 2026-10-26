package com.piuda.wilson.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.piuda.wilson.domain.auth.repository.RefreshTokenRepository;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElderlyRepository elderlyRepository;

    @Autowired
    private CareRelationshipRepository careRelationshipRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    /** Redis 대신 토큰 무효화 기록을 메모리로 흉내낸다. */
    private final Map<Long, Long> revokedAt = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        willAnswer(invocation -> revokedAt.put(invocation.getArgument(0), invocation.getArgument(1)))
                .given(tokenRevocationRepository).revokeAllIssuedBefore(anyLong(), anyLong(), any());
        given(tokenRevocationRepository.findRevokedAtEpochMilli(anyLong()))
                .willAnswer(invocation -> Optional.ofNullable(revokedAt.get(invocation.<Long>getArgument(0))));
    }

    @Test
    @DisplayName("로그아웃하면 같은 토큰은 즉시 거절되고, 이미 로그아웃한 토큰으로 다시 로그아웃할 수 없다")
    void logout() throws Exception {
        // given
        String token = signUpAndLogin("logout@wilson.com");
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // when
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // then
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃 직후 다시 로그인해서 받은 토큰은 유효하다")
    void loginRightAfterLogout() throws Exception {
        // given
        String token = signUpAndLogin("relogin@wilson.com");
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // when
        String newToken = login("relogin@wilson.com");

        // then
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("탈퇴하면 토큰·로그인이 막히고 같은 이메일로 재가입할 수 있으며, 노인은 연결만 끊긴 채 남는다")
    void withdraw() throws Exception {
        // given
        String token = signUpAndLogin("leave@wilson.com");
        Integer elderlyId = registerElderly(token);

        // when
        mockMvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // then: 토큰과 로그인이 막힘
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"leave@wilson.com","password":"raw-password"}
                                """))
                .andExpect(status().isUnauthorized());

        // then: 노인은 남고 연결만 끊김
        assertThat(elderlyRepository.findByIdAndDeletedFalse(elderlyId.longValue())).isPresent();
        assertThat(careRelationshipRepository.findByElderlyId(elderlyId.longValue())).isEmpty();

        // then: 같은 이메일로 재가입 가능
        String rejoinedToken = signUpAndLogin("leave@wilson.com");
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + rejoinedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("leave@wilson.com"));
    }

    private Integer registerElderly(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/elderlies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김철수","birth":"1945-03-11","gender":"MALE","relationship":"FATHER","legalBasis":"FAMILY_DELEGATION","consents":[{"type":"SENSITIVE_INFO","agreed":true,"version":1}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.elderlyId");
    }

    private String signUpAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"raw-password","name":"보호자","memberType":"CAREGIVER"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"raw-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
