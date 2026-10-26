package com.piuda.wilson.domain.conversation.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;

class VoiceHandshakeInterceptorTest {

    private final VoiceHandshakeInterceptor interceptor = new VoiceHandshakeInterceptor();
    private final MockHttpServletResponse servletResponse = new MockHttpServletResponse();
    private final Map<String, Object> attributes = new HashMap<>();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private boolean handshake(String path) {
        return interceptor.beforeHandshake(
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", path)),
                new ServletServerHttpResponse(servletResponse),
                mock(WebSocketHandler.class),
                attributes);
    }

    private void login(Long memberId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
    }

    @Test
    @DisplayName("인증된 회원 ID와 경로의 대화 ID를 세션 속성에 담는다")
    void handshake_success() {
        // given
        login(5L);

        // when
        boolean accepted = handshake("/ws/v1/conversations/100/voice");

        // then
        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(VoiceHandshakeInterceptor.MEMBER_ID, 5L)
                .containsEntry(VoiceHandshakeInterceptor.CONVERSATION_ID, 100L);
    }

    @Test
    @DisplayName("인증 정보가 없으면 401로 거절한다")
    void handshake_unauthenticated() {
        // when
        boolean accepted = handshake("/ws/v1/conversations/100/voice");

        // then
        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(401);
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("대화 ID가 숫자가 아니면 400으로 거절한다")
    void handshake_invalidConversationId() {
        // given
        login(5L);

        // when
        boolean accepted = handshake("/ws/v1/conversations/abc/voice");

        // then
        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(400);
    }
}
