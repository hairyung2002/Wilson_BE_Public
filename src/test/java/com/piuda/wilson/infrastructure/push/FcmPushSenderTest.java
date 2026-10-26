package com.piuda.wilson.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FcmPushSenderTest {

    private static final String URL = "https://fcm.googleapis.com/v1/projects/wilson-test/messages:send";

    private MockRestServiceServer server;
    private FcmPushSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new FcmPushSender(builder.build(), () -> "access-token", "wilson-test");
    }

    @Test
    @DisplayName("알림 제목·본문과 데이터를 FCM HTTP v1 형식으로 보낸다")
    void send_notification() {
        // given
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(jsonPath("$.message.token").value("device-token"))
                .andExpect(jsonPath("$.message.notification.title").value("윌슨"))
                .andExpect(jsonPath("$.message.notification.body").value("오늘 대화 3번"))
                .andExpect(jsonPath("$.message.data.type").value("DAILY_SUMMARY"))
                .andRespond(withSuccess("{\"name\":\"projects/wilson-test/messages/1\"}", MediaType.APPLICATION_JSON));

        // when
        PushResult result = sender.send(new PushMessage("device-token", "윌슨", "오늘 대화 3번",
                Map.of("type", "DAILY_SUMMARY")));

        // then
        assertThat(result).isEqualTo(PushResult.SENT);
        server.verify();
    }

    @Test
    @DisplayName("제목이 없으면 화면 알림 없이 데이터만 보낸다 (선제 대화로 앱을 깨우는 용도)")
    void send_dataOnly() {
        // given
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.message.notification").doesNotExist())
                .andExpect(jsonPath("$.message.data.triggerId").value("t-1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when & then
        assertThat(sender.send(new PushMessage("device-token", null, null, Map.of("triggerId", "t-1"))))
                .isEqualTo(PushResult.SENT);
    }

    @Test
    @DisplayName("등록되지 않은 토큰(404)·잘못된 토큰(400)은 토큰을 지우도록 알린다")
    void send_invalidToken() {
        // given
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .body("{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}")
                .contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}").contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThat(sender.send(new PushMessage("gone", "t", "b", Map.of()))).isEqualTo(PushResult.INVALID_TOKEN);
        assertThat(sender.send(new PushMessage("bad", "t", "b", Map.of()))).isEqualTo(PushResult.INVALID_TOKEN);
    }

    @Test
    @DisplayName("FCM 서버 오류·인증 오류는 다시 시도할 수 있는 실패로 본다")
    void send_failure() {
        // given
        server.expect(requestTo(URL)).andRespond(withServerError());

        // when & then
        assertThat(sender.send(new PushMessage("device-token", "t", "b", Map.of()))).isEqualTo(PushResult.FAILED);
    }
}
