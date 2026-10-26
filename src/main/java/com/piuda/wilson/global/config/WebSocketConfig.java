package com.piuda.wilson.global.config;

import com.piuda.wilson.domain.conversation.websocket.VoiceHandshakeInterceptor;
import com.piuda.wilson.domain.conversation.websocket.VoiceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 음성 턴 WebSocket 설정.
 *
 * <p>프레임 크기 제한(바이너리 512KB, 텍스트 8KB)은 application.yaml의
 * {@code server.servlet.context-parameters}로 Tomcat에 설정한다. 한 턴 합계 제한은 VoiceWebSocketHandler가 검사한다.</p>
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final VoiceWebSocketHandler voiceWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceWebSocketHandler, "/ws/v1/conversations/*/voice")
                .addInterceptors(new VoiceHandshakeInterceptor());
    }
}
