package com.piuda.wilson.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
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
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.service.NotificationService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 결정 E·H: 응급은 보호자 전원, 나머지는 설정(기본 OWNER만)을 따르고, 보호자는 자기 알림만 본다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;

    @MockitoBean
    private InviteCodeRepository inviteCodeRepository;

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
    @DisplayName("응급은 OWNER·MEMBER 모두, 일반 알림은 켠 보호자만 받고, 각자 읽음 처리한다")
    void notificationFlow() throws Exception {
        // given: 아들(OWNER), 딸(MEMBER)
        String son = signUpAndLogin("son@wilson.com");
        Long elderlyId = registerElderly(son);
        String daughter = signUpAndLogin("daughter@wilson.com");
        String code = JsonPath.read(perform(post("/api/v1/elderlies/" + elderlyId + "/invite-code"), son, null)
                .andReturn().getResponse().getContentAsString(), "$.data.inviteCode");
        perform(post("/api/v1/care-relationships"), daughter, """
                {"inviteCode":"%s","relation":"딸"}
                """.formatted(code)).andExpect(status().isCreated());

        // when
        notificationService.notifyCaregivers(elderlyId, NotificationType.EMERGENCY, "응급", "바로 연락해 보세요");
        notificationService.notifyCaregivers(elderlyId, NotificationType.DAILY_SUMMARY, "김철수님의 오늘", "대화 3번");

        // then: 딸은 응급만, 아들은 둘 다
        perform(get("/api/v1/notifications"), daughter, null)
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("EMERGENCY"));
        perform(get("/api/v1/notifications"), son, null)
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].type").value("DAILY_SUMMARY"));

        // when: 딸이 하루 요약을 켠다
        perform(patch("/api/v1/elderlies/" + elderlyId + "/notification-settings"), daughter, """
                {"dailySummary":true}
                """).andExpect(jsonPath("$.data.dailySummary").value(true))
                .andExpect(jsonPath("$.data.patternChange").value(false));
        notificationService.notifyCaregivers(elderlyId, NotificationType.DAILY_SUMMARY, "김철수님의 오늘", "대화 1번");

        // then: 딸 안 읽은 수 2 → 모두 읽음 → 0, 아들 알림은 그대로
        perform(get("/api/v1/notifications/unread-count"), daughter, null).andExpect(jsonPath("$.data.count").value(2));
        perform(patch("/api/v1/notifications/read-all"), daughter, null).andExpect(status().isNoContent());
        perform(get("/api/v1/notifications/unread-count"), daughter, null).andExpect(jsonPath("$.data.count").value(0));
        perform(get("/api/v1/notifications/unread-count"), son, null).andExpect(jsonPath("$.data.count").value(3));

        // then: 남의 알림은 읽음 처리할 수 없다
        Integer sonsNotification = JsonPath.read(perform(get("/api/v1/notifications"), son, null)
                .andReturn().getResponse().getContentAsString(), "$.data.content[0].notificationId");
        perform(patch("/api/v1/notifications/" + sonsNotification + "/read"), daughter, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("N001"));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private Long registerElderly(String token) throws Exception {
        Integer id = JsonPath.read(perform(post("/api/v1/elderlies"), token, """
                {"name":"김철수","birth":"1945-03-11","gender":"MALE","relationship":"아들","legalBasis":"FAMILY_DELEGATION",
                 "consents":[{"type":"SENSITIVE_INFO","agreed":true,"version":1}]}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.elderlyId");
        return id.longValue();
    }

    private String signUpAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/members").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"raw-password","name":"보호자","memberType":"CAREGIVER"}
                        """.formatted(email)))
                .andExpect(status().isCreated());
        return JsonPath.read(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"raw-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.accessToken");
    }
}
