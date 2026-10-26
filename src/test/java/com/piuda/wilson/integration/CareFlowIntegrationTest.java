package com.piuda.wilson.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.piuda.wilson.domain.auth.repository.LoginAttemptRepository;
import com.piuda.wilson.domain.auth.repository.RefreshTokenRepository;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.caregiver.repository.InviteCodeRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 결정 0: 보호자는 노인별 초대 코드가 있어야만 연결된다. OWNER/MEMBER 권한 흐름까지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CareFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    /** 기본값 0회 — 시도 횟수 제한에 걸리지 않는다. */
    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;

    @MockitoBean
    private InviteCodeRepository inviteCodeRepository;

    /** Redis 대신 초대 코드를 메모리로 흉내낸다. */
    private final Map<String, Long> codes = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        given(inviteCodeRepository.issue(anyString(), anyLong(), any()))
                .willAnswer(invocation -> codes.putIfAbsent(invocation.getArgument(0), invocation.getArgument(1)) == null);
        given(inviteCodeRepository.findElderlyId(anyString()))
                .willAnswer(invocation -> Optional.ofNullable(codes.get(invocation.<String>getArgument(0))));
        willAnswer(invocation -> codes.remove(invocation.<String>getArgument(0)))
                .given(inviteCodeRepository).delete(anyString(), anyLong());
    }

    @Test
    @DisplayName("노인 ID만으로는 연결할 수 없고, 주보호자가 발급한 초대 코드로만 MEMBER로 연결된다")
    void inviteFlow() throws Exception {
        // given: 아들(OWNER)이 노인을 등록하고, 딸이 가입
        String son = signUpAndLogin("son@wilson.com");
        Integer elderlyId = registerElderly(son);
        String daughter = signUpAndLogin("daughter@wilson.com");

        // then: 노인 ID만으로는 연결 불가 (기존 보안 구멍)
        perform(post("/api/v1/care-relationships"), daughter, """
                {"elderlyId":%d,"relation":"딸"}
                """.formatted(elderlyId)).andExpect(status().isBadRequest());

        // when: 아들이 초대 코드를 발급하고 딸이 그 코드로 연결
        String code = JsonPath.read(perform(post("/api/v1/elderlies/" + elderlyId + "/invite-code"), son, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.inviteCode");
        Integer daughterLink = JsonPath.read(perform(post("/api/v1/care-relationships"), daughter, """
                {"inviteCode":"%s","relation":"딸"}
                """.formatted(code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andReturn().getResponse().getContentAsString(), "$.data.careRelationshipId");

        // then: 쓴 코드는 다시 쓸 수 없고, MEMBER는 초대 코드를 발급하지 못한다
        String stranger = signUpAndLogin("stranger@wilson.com");
        perform(post("/api/v1/care-relationships"), stranger, """
                {"inviteCode":"%s","relation":"이웃"}
                """.formatted(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("G003"));
        perform(post("/api/v1/elderlies/" + elderlyId + "/invite-code"), daughter, null)
                .andExpect(status().isForbidden());

        // then: 둘 다 보호자 목록을 보고, 연결된 딸은 노인 정보도 본다
        perform(get("/api/v1/elderlies/" + elderlyId + "/caregivers"), daughter, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[1].me").value(true));
        perform(get("/api/v1/elderlies/" + elderlyId), daughter, null).andExpect(status().isOk());
        perform(get("/api/v1/elderlies/" + elderlyId), stranger, null).andExpect(status().isForbidden());

        // then: 주보호자는 넘기기 전에는 나갈 수 없고, MEMBER는 노인을 삭제할 수 없다
        Integer sonLink = JsonPath.read(perform(get("/api/v1/elderlies/" + elderlyId + "/caregivers"), son, null)
                .andReturn().getResponse().getContentAsString(), "$.data[0].careRelationshipId");
        perform(delete("/api/v1/care-relationships/" + sonLink), son, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("G005"));
        perform(delete("/api/v1/elderlies/" + elderlyId), daughter, null).andExpect(status().isForbidden());

        // when: 아들이 딸에게 주보호자를 넘기고 나간다
        perform(patch("/api/v1/elderlies/" + elderlyId + "/owner"), son, """
                {"careRelationshipId":%d}
                """.formatted(daughterLink)).andExpect(status().isNoContent());
        perform(delete("/api/v1/care-relationships/" + sonLink), son, null).andExpect(status().isNoContent());

        // then: 이제 딸이 주보호자로 초대 코드를 발급하고, 아들은 노인에 접근할 수 없다
        perform(post("/api/v1/elderlies/" + elderlyId + "/invite-code"), daughter, null)
                .andExpect(status().isCreated());
        perform(get("/api/v1/elderlies/" + elderlyId), son, null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("토큰 없이 연결을 시도하면 401을 반환한다")
    void createCareRelationship_withoutToken_unauthorized() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/care-relationships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteCode":"K7Q2M9XD","relation":"자녀"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private Integer registerElderly(String token) throws Exception {
        MvcResult result = perform(post("/api/v1/elderlies"), token, """
                {"name":"김철수","birth":"1945-03-11","gender":"MALE","relationship":"아들","legalBasis":"FAMILY_DELEGATION","consents":[{"type":"SENSITIVE_INFO","agreed":true,"version":1}]}
                """).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.elderlyId");
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
