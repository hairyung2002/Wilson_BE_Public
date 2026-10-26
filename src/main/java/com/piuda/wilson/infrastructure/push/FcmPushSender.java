package com.piuda.wilson.infrastructure.push;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * FCM HTTP v1 발송 ({@code POST /v1/projects/{projectId}/messages:send}).
 *
 * <p>404(UNREGISTERED)·400(INVALID_ARGUMENT)은 토큰 문제로 보고 지우게 한다.
 * 그 외 오류(인증·429·5xx·네트워크)는 다시 보내면 될 수 있는 실패다. 토큰 값은 로그에 남기지 않는다.</p>
 */
@Slf4j
public class FcmPushSender implements PushSender {

    private final RestClient restClient;
    private final FcmAccessTokenProvider accessTokenProvider;
    private final String sendUrl;

    public FcmPushSender(RestClient restClient, FcmAccessTokenProvider accessTokenProvider, String projectId) {
        this.restClient = restClient;
        this.accessTokenProvider = accessTokenProvider;
        this.sendUrl = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";
    }

    @Override
    public PushResult send(PushMessage message) {
        try {
            restClient.post()
                    .uri(sendUrl)
                    .header("Authorization", "Bearer " + accessTokenProvider.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", toFcmMessage(message)))
                    .retrieve()
                    .toBodilessEntity();
            return PushResult.SENT;
        } catch (RestClientResponseException e) {
            return classify(e.getStatusCode());
        } catch (RestClientException | IllegalStateException e) {
            log.warn("FCM push failed: reason={}", e.getClass().getSimpleName());
            return PushResult.FAILED;
        }
    }

    private static PushResult classify(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND) || status.isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            return PushResult.INVALID_TOKEN;
        }
        log.warn("FCM push failed: status={}", status.value());
        return PushResult.FAILED;
    }

    private static Map<String, Object> toFcmMessage(PushMessage message) {
        Map<String, Object> fcm = new LinkedHashMap<>();
        fcm.put("token", message.token());
        if (message.title() != null) {
            fcm.put("notification", Map.of("title", message.title(), "body", message.body()));
        }
        if (message.data() != null && !message.data().isEmpty()) {
            fcm.put("data", message.data());
        }
        return fcm;
    }
}
