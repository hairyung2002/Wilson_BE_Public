package com.piuda.wilson.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.piuda.wilson.domain.auth.repository.DeviceCodeRepository;
import com.piuda.wilson.domain.auth.repository.LoginAttemptRepository;
import com.piuda.wilson.domain.auth.repository.RefreshTokenRepository;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.conversation.repository.ConversationContextRepository;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationFlowIntegrationTest {

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

    @MockitoBean
    private ConversationContextRepository conversationContextRepository;

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
    @DisplayName("노인 기기가 대화를 시작·종료하고 보호자가 목록·상세를 조회하고 삭제한다")
    void conversationLifecycle() throws Exception {
        // given: 보호자가 노인을 등록하고 노인 기기가 로그인
        String caregiverToken = signUpAndLogin("caregiver@wilson.com");
        Integer elderlyId = registerElderly(caregiverToken);
        String elderlyToken = deviceLogin(issueDeviceCode(caregiverToken, elderlyId));

        // when: 대화를 시작한 뒤 종료하지 않고 새 대화 시작 (앱 비정상 종료 상황)
        Integer dangling = startConversation(elderlyToken, "MEAL");
        Integer current = startConversation(elderlyToken, "DAILY");

        // then: 보호자 목록에서 최신순으로 보이고, 남아 있던 대화는 자동 종료됨
        mockMvc.perform(get("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + caregiverToken)
                        .param("elderlyId", String.valueOf(elderlyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].conversationId").value(current))
                .andExpect(jsonPath("$.data.content[0].state").value("WAITING"))
                .andExpect(jsonPath("$.data.content[1].conversationId").value(dangling))
                .andExpect(jsonPath("$.data.content[1].state").value("FINISHED"));

        // when & then: 노인 기기가 종료, 재종료는 409
        mockMvc.perform(patch("/api/v1/conversations/" + current + "/finish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + elderlyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("FINISHED"));
        mockMvc.perform(patch("/api/v1/conversations/" + current + "/finish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + elderlyToken))
                .andExpect(status().isConflict());

        // when & then: 보호자가 상세 조회 후 삭제하면 목록에서 사라짐
        mockMvc.perform(get("/api/v1/conversations/" + current)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationType").value("DAILY"))
                .andExpect(jsonPath("$.data.messages.length()").value(0));
        mockMvc.perform(delete("/api/v1/conversations/" + current)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + caregiverToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + caregiverToken)
                        .param("elderlyId", String.valueOf(elderlyId)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("보호자 토큰으로는 대화를 시작할 수 없고, 연결되지 않은 보호자는 목록을 볼 수 없다")
    void permissions() throws Exception {
        // given
        String caregiverToken = signUpAndLogin("owner@wilson.com");
        String otherToken = signUpAndLogin("other@wilson.com");
        Integer elderlyId = registerElderly(caregiverToken);

        // when & then
        mockMvc.perform(post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + caregiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationType":"DAILY"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .param("elderlyId", String.valueOf(elderlyId)))
                .andExpect(status().isForbidden());
    }

    private Integer startConversation(String token, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationType":"%s"}
                                """.formatted(type)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.conversationId");
    }

    private String deviceLogin(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/device-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode":"%s"}
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
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
