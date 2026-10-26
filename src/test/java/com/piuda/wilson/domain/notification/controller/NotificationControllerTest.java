package com.piuda.wilson.domain.notification.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.notification.dto.NotificationResponse;
import com.piuda.wilson.domain.notification.dto.NotificationSettingsRequest;
import com.piuda.wilson.domain.notification.dto.NotificationSettingsResponse;
import com.piuda.wilson.domain.notification.dto.UnreadCountResponse;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.service.NotificationService;
import com.piuda.wilson.domain.notification.service.NotificationSettingsService;
import com.piuda.wilson.global.common.response.PageResponse;
import com.piuda.wilson.global.config.SecurityConfig;
import com.piuda.wilson.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSettingsService notificationSettingsService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    private static RequestPostProcessor member() {
        return authentication(new UsernamePasswordAuthenticationToken(5L, null, List.of()));
    }

    @Test
    @DisplayName("내 알림 목록을 페이지로 반환한다")
    void getNotifications() throws Exception {
        // given
        given(notificationService.getNotifications(5L, PageRequest.of(0, 20))).willReturn(new PageResponse<>(0, 20, 1, 1,
                List.of(new NotificationResponse(7L, NotificationType.DAILY_SUMMARY, 15L, "김철수님의 오늘",
                        "오늘 대화 3번, 12분 나누셨어요", false, LocalDateTime.of(2026, 10, 20, 20, 0)))));

        // when & then
        mockMvc.perform(get("/api/v1/notifications").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].notificationId").value(7))
                .andExpect(jsonPath("$.data.content[0].type").value("DAILY_SUMMARY"))
                .andExpect(jsonPath("$.data.content[0].read").value(false));
    }

    @Test
    @DisplayName("읽음·모두 읽음은 204, 안 읽은 수를 반환한다")
    void readAndCount() throws Exception {
        // given
        given(notificationService.countUnread(5L)).willReturn(new UnreadCountResponse(3));

        // when & then
        mockMvc.perform(patch("/api/v1/notifications/7/read").with(member())).andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/v1/notifications/read-all").with(member())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));
        verify(notificationService).markRead(5L, 7L);
        verify(notificationService).markAllRead(5L);
    }

    @Test
    @DisplayName("노인별 내 알림 설정을 보고 바꾼다")
    void settings() throws Exception {
        // given
        given(notificationSettingsService.get(5L, 15L)).willReturn(new NotificationSettingsResponse(true, true, true));
        given(notificationSettingsService.update(5L, 15L, new NotificationSettingsRequest(null, false, null)))
                .willReturn(new NotificationSettingsResponse(true, false, true));

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/notification-settings").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patternChange").value(true));
        mockMvc.perform(patch("/api/v1/elderlies/15/notification-settings").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proactiveNoResponse":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proactiveNoResponse").value(false));
    }

    @Test
    @DisplayName("인증 없이는 401")
    void unauthorized() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }
}
