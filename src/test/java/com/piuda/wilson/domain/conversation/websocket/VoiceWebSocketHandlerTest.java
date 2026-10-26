package com.piuda.wilson.domain.conversation.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.conversation.service.VoiceTurnService;
import com.piuda.wilson.domain.conversation.service.VoiceTurnSink;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.infrastructure.ai.DialogueFailure;
import com.piuda.wilson.infrastructure.ai.DialogueFailureStage;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

class VoiceWebSocketHandlerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CONVERSATION_ID = 100L;

    private final VoiceTurnService voiceTurnService = mock(VoiceTurnService.class);
    private final VoiceWebSocketHandler handler = new VoiceWebSocketHandler(voiceTurnService, JsonMapper.builder().build());
    private final WebSocketSession session = mock(WebSocketSession.class);
    private final List<WebSocketMessage<?>> sent = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(VoiceHandshakeInterceptor.MEMBER_ID, MEMBER_ID);
        attributes.put(VoiceHandshakeInterceptor.CONVERSATION_ID, CONVERSATION_ID);
        given(session.getAttributes()).willReturn(attributes);
        given(session.getId()).willReturn("session-1");
        given(session.isOpen()).willReturn(true);
        org.mockito.BDDMockito.willAnswer(invocation -> sent.add(invocation.getArgument(0)))
                .given(session).sendMessage(any());
        handler.afterConnectionEstablished(session);
    }

    private void text(String payload) throws Exception {
        handler.handleMessage(session, new TextMessage(payload));
    }

    private void binary(byte[] payload) throws Exception {
        handler.handleMessage(session, new BinaryMessage(payload));
    }

    private String lastText() {
        return ((TextMessage) sent.get(sent.size() - 1)).getPayload();
    }

    private VoiceTurnSink completeTurnAndCaptureSink() throws Exception {
        text("""
                {"type":"TURN_START","audioFormat":"wav"}
                """);
        binary(new byte[]{1, 2});
        binary(new byte[]{3});
        text("""
                {"type":"TURN_END"}
                """);
        ArgumentCaptor<VoiceTurnSink> sink = ArgumentCaptor.forClass(VoiceTurnSink.class);
        verify(voiceTurnService).handleTurn(eq(MEMBER_ID), eq(CONVERSATION_ID), eq(new byte[]{1, 2, 3}),
                eq("wav"), sink.capture());
        return sink.getValue();
    }

    @Test
    @DisplayName("TURN_START·오디오 프레임·TURN_END를 받으면 합친 오디오로 음성 턴을 처리한다")
    void turn_success() throws Exception {
        // when & then
        completeTurnAndCaptureSink();
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("턴 결과를 TRANSCRIPT·바이너리 음성·COMPLETED 프레임으로 앱에 보낸다")
    void sink_forwardsEvents() throws Exception {
        // given
        VoiceTurnSink sink = completeTurnAndCaptureSink();

        // when
        sink.sendTranscript("밥 먹었어");
        sink.sendAudioChunk(new byte[]{9, 8}, 0);
        sink.sendCompleted("잘하셨어요");

        // then
        assertThat(((TextMessage) sent.get(0)).getPayload()).isEqualTo("{\"type\":\"TRANSCRIPT\",\"text\":\"밥 먹었어\"}");
        ByteBuffer audio = ((BinaryMessage) sent.get(1)).getPayload();
        assertThat(audio.remaining()).isEqualTo(2);
        assertThat(((TextMessage) sent.get(2)).getPayload()).isEqualTo("{\"type\":\"COMPLETED\",\"text\":\"잘하셨어요\"}");
    }

    @Test
    @DisplayName("응급 감지는 EMERGENCY 프레임으로 알린다 (앱이 내장 안내 음성을 먼저 재생)")
    void sink_forwardsEmergency() throws Exception {
        // given
        VoiceTurnSink sink = completeTurnAndCaptureSink();

        // when
        sink.sendEmergency(EmergencyCategory.FALL);

        // then
        assertThat(((TextMessage) sent.get(0)).getPayload()).isEqualTo("{\"type\":\"EMERGENCY\",\"category\":\"FALL\"}");
    }

    @Test
    @DisplayName("실패는 단계·복구 가능 여부와 함께 FAILED 프레임으로 보내고, 답변 텍스트가 없으면 생략한다")
    void sink_forwardsFailure() throws Exception {
        // given
        VoiceTurnSink sink = completeTurnAndCaptureSink();

        // when
        sink.sendFailed(new DialogueFailure(DialogueFailureStage.TTS, true, "잘하셨어요"));
        sink.sendFailed(DialogueFailure.unavailable());

        // then
        assertThat(((TextMessage) sent.get(0)).getPayload())
                .isEqualTo("{\"type\":\"FAILED\",\"stage\":\"TTS\",\"recoverable\":true,\"text\":\"잘하셨어요\"}");
        assertThat(((TextMessage) sent.get(1)).getPayload())
                .isEqualTo("{\"type\":\"FAILED\",\"stage\":\"UNAVAILABLE\",\"recoverable\":false}");
    }

    @Test
    @DisplayName("턴을 시작할 수 없으면 ERROR 프레임으로 에러 코드를 알린다")
    void turn_rejected() throws Exception {
        // given
        willThrow(new CustomException(ErrorCode.CONVERSATION_TURN_IN_PROGRESS))
                .given(voiceTurnService).handleTurn(any(), any(), any(), anyString(), any());

        // when
        text("""
                {"type":"TURN_START","audioFormat":"wav"}
                """);
        binary(new byte[]{1});
        text("""
                {"type":"TURN_END"}
                """);

        // then
        assertThat(lastText()).isEqualTo(
                "{\"type\":\"ERROR\",\"code\":\"CV003\",\"message\":\"이전 대화 응답을 처리하고 있습니다.\"}");
    }

    @Test
    @DisplayName("PROACTIVE_START는 첫 안부를 요청하고, 안부 음성·문장을 같은 연결로 보낸다 (J4)")
    void proactiveStart() throws Exception {
        // when
        text("""
                {"type":"PROACTIVE_START"}
                """);
        ArgumentCaptor<VoiceTurnSink> sink = ArgumentCaptor.forClass(VoiceTurnSink.class);
        verify(voiceTurnService).startProactiveGreeting(eq(MEMBER_ID), eq(CONVERSATION_ID), sink.capture());
        sink.getValue().sendAudioChunk(new byte[]{7}, 1);
        sink.getValue().sendCompleted("할머니, 오늘 점심은 뭐 드셨어요?");

        // then
        assertThat(sent.get(0)).isInstanceOf(BinaryMessage.class);
        assertThat(lastText()).isEqualTo("{\"type\":\"COMPLETED\",\"text\":\"할머니, 오늘 점심은 뭐 드셨어요?\"}");
    }

    @Test
    @DisplayName("첫 안부를 시작할 수 없으면(이미 말이 오간 대화 등) ERROR 프레임으로 알린다")
    void proactiveStart_rejected() throws Exception {
        // given
        willThrow(new CustomException(ErrorCode.VOICE_PROTOCOL_VIOLATION))
                .given(voiceTurnService).startProactiveGreeting(any(), any(), any());

        // when
        text("""
                {"type":"PROACTIVE_START"}
                """);

        // then
        assertThat(lastText()).contains("\"code\":\"CV005\"");
    }

    @Test
    @DisplayName("TURN_START 없이 오디오를 보내면 프로토콜 오류를 알린다")
    void binaryWithoutStart() throws Exception {
        // when
        binary(new byte[]{1});

        // then
        assertThat(lastText()).contains("\"code\":\"CV005\"");
    }

    @Test
    @DisplayName("TURN_START 없이 TURN_END를 보내면 프로토콜 오류를 알리고 턴을 처리하지 않는다")
    void endWithoutStart() throws Exception {
        // when
        text("""
                {"type":"TURN_END"}
                """);

        // then
        assertThat(lastText()).contains("\"code\":\"CV005\"");
        verify(voiceTurnService, never()).handleTurn(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("알 수 없는 메시지나 JSON이 아닌 텍스트는 프로토콜 오류를 알린다")
    void malformedText() throws Exception {
        // when
        text("hello");
        String first = lastText();
        text("""
                {"type":"DANCE"}
                """);

        // then
        assertThat(first).contains("\"code\":\"CV005\"");
        assertThat(lastText()).contains("\"code\":\"CV005\"");
    }

    @Test
    @DisplayName("한 턴의 오디오가 10MB를 넘으면 오류를 알리고 받은 오디오를 버린다")
    void audioTooLarge() throws Exception {
        // given
        text("""
                {"type":"TURN_START","audioFormat":"wav"}
                """);

        // when
        binary(new byte[VoiceWebSocketHandler.MAX_AUDIO_BYTES]);
        binary(new byte[1]);
        text("""
                {"type":"TURN_END"}
                """);

        // then
        assertThat(((TextMessage) sent.get(0)).getPayload()).contains("\"code\":\"CV006\"");
        assertThat(lastText()).contains("\"code\":\"CV005\"");
        verify(voiceTurnService, never()).handleTurn(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("연결이 끊긴 뒤의 전송은 예외 없이 무시한다")
    void sink_closedSession() throws Exception {
        // given
        VoiceTurnSink sink = completeTurnAndCaptureSink();
        given(session.isOpen()).willReturn(false);

        // when
        sink.sendCompleted("잘하셨어요");

        // then
        assertThat(sent).isEmpty();
    }
}
