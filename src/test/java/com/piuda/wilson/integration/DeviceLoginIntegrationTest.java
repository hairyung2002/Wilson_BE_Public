package com.piuda.wilson.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.piuda.wilson.domain.auth.repository.DeviceCodeRepository;
import com.piuda.wilson.domain.auth.repository.LoginAttemptRepository;
import com.piuda.wilson.domain.auth.repository.RefreshTokenRepository;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeviceLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    @MockitoBean
    private DeviceCodeRepository deviceCodeRepository;

    /** 기본값 0회 — 시도 횟수 제한에 걸리지 않는다. */
    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;

    /** Redis 대신 일회성 코드 저장소를 메모리로 흉내낸다. */
    private final Map<String, Long> codeStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        given(deviceCodeRepository.saveIfAbsent(anyString(), anyLong(), any()))
                .willAnswer(invocation -> codeStore.putIfAbsent(invocation.getArgument(0),
                        invocation.getArgument(1)) == null);
        given(deviceCodeRepository.consume(anyString()))
                .willAnswer(invocation -> Optional.ofNullable(codeStore.remove(invocation.<String>getArgument(0))));
    }

    @Test
    @DisplayName("보호자가 발급한 코드로 노인 기기가 로그인하고, 코드는 한 번만 쓸 수 있다")
    void issueCodeAndDeviceLogin() throws Exception {
        // given
        String caregiverToken = signUpAndLogin("caregiver@wilson.com");
        Integer elderlyId = registerElderly(caregiverToken);

        // when: 첫 기기 로그인
        String code = issueDeviceCode(caregiverToken, elderlyId);
        MvcResult first = deviceLogin(code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberType").value("ELDERLY"))
                .andReturn();
        Integer memberId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.memberId");

        // then: 같은 코드 재사용 불가
        deviceLogin(code).andExpect(status().isUnauthorized());

        // when & then: 기기를 다시 연결해도 같은 노인 회원으로 로그인
        String secondCode = issueDeviceCode(caregiverToken, elderlyId);
        MvcResult second = deviceLogin(secondCode).andExpect(status().isOk()).andReturn();
        Integer secondMemberId = JsonPath.read(second.getResponse().getContentAsString(), "$.data.memberId");
        assertThat(secondMemberId).isEqualTo(memberId);
    }

    @Test
    @DisplayName("연결되지 않은 보호자는 기기 연결 코드를 발급할 수 없다")
    void issueCode_otherCaregiver_forbidden() throws Exception {
        // given
        String ownerToken = signUpAndLogin("owner@wilson.com");
        String otherToken = signUpAndLogin("other@wilson.com");
        Integer elderlyId = registerElderly(ownerToken);

        // when & then
        mockMvc.perform(post("/api/v1/elderlies/" + elderlyId + "/device-code")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    private ResultActions deviceLogin(String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/device-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"deviceCode":"%s"}
                        """.formatted(code)));
    }

    private String issueDeviceCode(String token, Integer elderlyId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/elderlies/" + elderlyId + "/device-code")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.deviceCode");
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
