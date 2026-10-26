package com.piuda.wilson.domain.conversation.websocket;

import com.piuda.wilson.domain.conversation.service.VoiceTurnService;
import com.piuda.wilson.domain.conversation.service.VoiceTurnSink;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.infrastructure.ai.DialogueFailure;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** 음성 턴 WebSocket 엔드포인트 (docs/websocket-voice-protocol.md). */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {

    public static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;

    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 5 * 1024 * 1024;
    private static final String OUTBOUND = "voice.outbound";
    private static final String TURN_BUFFER = "voice.turnBuffer";

    private final VoiceTurnService voiceTurnService;
    private final JsonMapper jsonMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // AI 응답은 gRPC 스레드에서 전송되므로 동시 전송에 안전한 데코레이터를 쓴다.
        session.getAttributes().put(OUTBOUND,
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_LIMIT_BYTES));
        log.info("Voice socket opened: sessionId={}, conversationId={}",
                session.getId(), session.getAttributes().get(VoiceHandshakeInterceptor.CONVERSATION_ID));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        VoiceClientMessage clientMessage = parse(message.getPayload());
        if (clientMessage == null || clientMessage.type() == null) {
            sendError(session, ErrorCode.VOICE_PROTOCOL_VIOLATION);
            return;
        }
        switch (clientMessage.type()) {
            case VoiceClientMessage.TURN_START -> startTurn(session, clientMessage.audioFormat());
            case VoiceClientMessage.TURN_END -> endTurn(session);
            case VoiceClientMessage.PROACTIVE_START -> startProactiveGreeting(session);
            default -> sendError(session, ErrorCode.VOICE_PROTOCOL_VIOLATION);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        TurnBuffer buffer = (TurnBuffer) session.getAttributes().get(TURN_BUFFER);
        if (buffer == null) {
            sendError(session, ErrorCode.VOICE_PROTOCOL_VIOLATION);
            return;
        }
        ByteBuffer payload = message.getPayload();
        if (buffer.size() + payload.remaining() > MAX_AUDIO_BYTES) {
            session.getAttributes().remove(TURN_BUFFER);
            sendError(session, ErrorCode.AUDIO_TOO_LARGE);
            return;
        }
        buffer.append(payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Voice socket transport error: sessionId={}, cause={}",
                session.getId(), exception.getClass().getSimpleName());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Voice socket closed: sessionId={}, status={}", session.getId(), status.getCode());
    }

    private void startTurn(WebSocketSession session, String audioFormat) {
        if (session.getAttributes().containsKey(TURN_BUFFER)) {
            log.warn("Voice turn restarted before TURN_END: sessionId={}", session.getId());
        }
        session.getAttributes().put(TURN_BUFFER, new TurnBuffer(audioFormat == null ? "" : audioFormat));
    }

    private void endTurn(WebSocketSession session) {
        TurnBuffer buffer = (TurnBuffer) session.getAttributes().remove(TURN_BUFFER);
        if (buffer == null) {
            sendError(session, ErrorCode.VOICE_PROTOCOL_VIOLATION);
            return;
        }
        Long memberId = (Long) session.getAttributes().get(VoiceHandshakeInterceptor.MEMBER_ID);
        Long conversationId = (Long) session.getAttributes().get(VoiceHandshakeInterceptor.CONVERSATION_ID);
        try {
            voiceTurnService.handleTurn(memberId, conversationId, buffer.toByteArray(), buffer.audioFormat(),
                    new SocketSink(session));
        } catch (CustomException e) {
            log.warn("Voice turn rejected: conversationId={}, code={}", conversationId, e.getErrorCode().getCode());
            sendError(session, e.getErrorCode());
        } catch (RuntimeException e) {
            log.error("Voice turn failed to start: conversationId={}", conversationId, e);
            sendError(session, ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void startProactiveGreeting(WebSocketSession session) {
        Long memberId = (Long) session.getAttributes().get(VoiceHandshakeInterceptor.MEMBER_ID);
        Long conversationId = (Long) session.getAttributes().get(VoiceHandshakeInterceptor.CONVERSATION_ID);
        try {
            voiceTurnService.startProactiveGreeting(memberId, conversationId, new SocketSink(session));
        } catch (CustomException e) {
            log.info("Proactive greeting rejected: conversationId={}, code={}", conversationId,
                    e.getErrorCode().getCode());
            sendError(session, e.getErrorCode());
        } catch (RuntimeException e) {
            log.error("Proactive greeting failed to start: conversationId={}", conversationId, e);
            sendError(session, ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private VoiceClientMessage parse(String payload) {
        try {
            return jsonMapper.readValue(payload, VoiceClientMessage.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    private void sendError(WebSocketSession session, ErrorCode errorCode) {
        send(session, toText(VoiceServerMessage.error(errorCode)));
    }

    private TextMessage toText(VoiceServerMessage message) {
        return new TextMessage(jsonMapper.writeValueAsString(message));
    }

    /** 연결이 끊겼거나 전송이 실패해도 예외를 던지지 않는다. 턴 결과는 이미 DB에 남아 있다. */
    private void send(WebSocketSession session, WebSocketMessage<?> message) {
        WebSocketSession outbound = (WebSocketSession) session.getAttributes().getOrDefault(OUTBOUND, session);
        if (!outbound.isOpen()) {
            log.debug("Voice socket closed, message dropped: sessionId={}", session.getId());
            return;
        }
        try {
            outbound.sendMessage(message);
        } catch (IOException | RuntimeException e) {
            log.warn("Voice socket send failed: sessionId={}, cause={}",
                    session.getId(), e.getClass().getSimpleName());
        }
    }

    private class SocketSink implements VoiceTurnSink {

        private final WebSocketSession session;

        SocketSink(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void sendTranscript(String sttText) {
            send(session, toText(VoiceServerMessage.transcript(sttText)));
        }

        @Override
        public void sendEmergency(EmergencyCategory category) {
            send(session, toText(VoiceServerMessage.emergency(category)));
        }

        @Override
        public void sendAudioChunk(byte[] data, int sequence) {
            send(session, new BinaryMessage(data));
        }

        @Override
        public void sendCompleted(String fullLlmResponseText) {
            send(session, toText(VoiceServerMessage.completed(fullLlmResponseText)));
        }

        @Override
        public void sendFailed(DialogueFailure failure) {
            send(session, toText(VoiceServerMessage.failed(failure)));
        }
    }

    private static class TurnBuffer {

        private final String audioFormat;
        private final ByteArrayOutputStream audio = new ByteArrayOutputStream();

        TurnBuffer(String audioFormat) {
            this.audioFormat = audioFormat;
        }

        String audioFormat() {
            return audioFormat;
        }

        int size() {
            return audio.size();
        }

        void append(ByteBuffer payload) {
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            audio.writeBytes(bytes);
        }

        byte[] toByteArray() {
            return audio.toByteArray();
        }
    }
}
