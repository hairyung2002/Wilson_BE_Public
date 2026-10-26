package com.piuda.wilson.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.piuda.wilson.domain.auth.repository.RefreshTokenRepository;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
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
class ElderlyFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    @Test
    @DisplayName("보호자가 노인을 등록·조회·수정·삭제한다")
    void registerReadUpdateDelete() throws Exception {
        // given
        String token = signUpAndLogin("caregiver@wilson.com");

        // when: 등록
        MvcResult created = mockMvc.perform(post("/api/v1/elderlies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김철수","birth":"1945-03-11","gender":"MALE","relationship":"FATHER","legalBasis":"FAMILY_DELEGATION","consents":[{"type":"SENSITIVE_INFO","agreed":true,"version":1}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Integer elderlyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.elderlyId");

        // then: 목록·상세
        mockMvc.perform(get("/api/v1/elderlies").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].relationship").value("FATHER"));
        mockMvc.perform(get("/api/v1/elderlies/" + elderlyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birth").value("1945-03-11"))
                .andExpect(jsonPath("$.data.gender").value("MALE"));

        // when & then: 수정
        mockMvc.perform(patch("/api/v1/elderlies/" + elderlyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김철순"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김철순"))
                .andExpect(jsonPath("$.data.relationship").value("FATHER"));

        // when & then: 삭제 후 목록에서 사라지고 상세는 404
        mockMvc.perform(delete("/api/v1/elderlies/" + elderlyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/elderlies").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/elderlies/" + elderlyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("다른 보호자가 등록한 노인은 조회할 수 없다")
    void otherCaregiver_forbidden() throws Exception {
        // given
        String ownerToken = signUpAndLogin("owner@wilson.com");
        String otherToken = signUpAndLogin("other@wilson.com");
        MvcResult created = mockMvc.perform(post("/api/v1/elderlies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김철수","birth":"1945-03-11","gender":"MALE","relationship":"FATHER","legalBasis":"FAMILY_DELEGATION","consents":[{"type":"SENSITIVE_INFO","agreed":true,"version":1}]}
                                """))
                .andReturn();
        Integer elderlyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.elderlyId");

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/" + elderlyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    private String signUpAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"raw-password","name":"보호자","memberType":"CAREGIVER"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
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
