package com.piuda.wilson.domain.conversation.websocket;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriTemplate;

/** 핸드셰이크 시점에 JWT 인증 결과(회원 ID)와 경로의 대화 ID를 세션 속성으로 옮긴다. */
@Slf4j
public class VoiceHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PATH = "/ws/v1/conversations/{conversationId}/voice";
    public static final String MEMBER_ID = "voice.memberId";
    public static final String CONVERSATION_ID = "voice.conversationId";

    private static final UriTemplate PATH_TEMPLATE = new UriTemplate(PATH);

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long memberId)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long conversationId = parseConversationId(request.getURI().getPath());
        if (conversationId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        attributes.put(MEMBER_ID, memberId);
        attributes.put(CONVERSATION_ID, conversationId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private Long parseConversationId(String path) {
        if (!PATH_TEMPLATE.matches(path)) {
            return null;
        }
        try {
            return Long.valueOf(PATH_TEMPLATE.match(path).get("conversationId"));
        } catch (NumberFormatException e) {
            log.warn("Invalid conversationId in voice path");
            return null;
        }
    }
}
