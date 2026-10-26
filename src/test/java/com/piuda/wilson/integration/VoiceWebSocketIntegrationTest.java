package com.piuda.wilson.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

import com.jayway.jsonpath.JsonPath;
import com.piuda.wilson.domain.auth.repository.DeviceCodeRepository;
import com.piuda.wilson.domain.auth.repository.LoginAttemptRepository;
import com.piuda.wilson.domain.auth.repository.RefreshTokenRepository;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import com.piuda.wilson.domain.conversation.repository.ConversationContextRepository;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.conversation.repository.MessageRepository;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.infrastructure.ai.DialogueClient;
import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import com.piuda.wilson.infrastructure.ai.DialogueListener;
import com.piuda.wilson.infrastructure.ai.DialogueRequest;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/** 실제 서버를 띄워 JWT 핸드셰이크 → 음성 턴 → AI 응답 중계 → DB 기록까지 검증한다. AI 서버만 가짜로 대체한다. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class VoiceWebSocketIntegrationTest {

    /** Tomcat 기본 바이너리 버퍼(8KB)를 넘는 크기. 프레임 크기 설정이 적용되지 않으면 연결이 끊긴다. */
    private static final int AUDIO_FRAME_BYTES = 400 * 1024;

    @LocalServerPort
    private int port;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ElderlyRepository elderlyRepository;

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
    private DialogueClient dialogueClient;

    @MockitoBean
    private ConversationContextRepository conversationContextRepository;

    private final Map<String, Long> codeStore = new ConcurrentHashMap<>();
    /** Redis 대화 맥락을 메모리로 흉내낸다 (append는 키가 있을 때만 — RPUSHX). */
    private final Map<Long, List<DialogueHistoryTurn>> contextStore = new ConcurrentHashMap<>();
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.create("http://localhost:" + port);
        given(deviceCodeRepository.saveIfAbsent(anyString(), anyLong(), any()))
                .willAnswer(invocation -> codeStore.putIfAbsent(invocation.getArgument(0),
                        invocation.getArgument(1)) == null);
        given(deviceCodeRepository.consume(anyString()))
                .willAnswer(invocation -> Optional.ofNullable(codeStore.remove(invocation.<String>getArgument(0))));
        given(conversationContextRepository.findRecentTurns(anyLong(), any()))
                .willAnswer(invocation -> Optional.ofNullable(contextStore.get(invocation.<Long>getArgument(0))));
        willAnswer(invocation -> {
            List<DialogueHistoryTurn> turns = invocation.getArgument(1);
            if (turns.isEmpty()) {
                contextStore.remove(invocation.<Long>getArgument(0));
            } else {
                contextStore.put(invocation.getArgument(0), new CopyOnWriteArrayList<>(turns));
            }
            return null;
        }).given(conversationContextRepository).replaceAll(anyLong(), any(), any());
        willAnswer(invocation -> {
            contextStore.computeIfPresent(invocation.getArgument(0), (id, turns) -> {
                turns.add(invocation.getArgument(1));
                return turns;
            });
            return null;
        }).given(conversationContextRepository).append(anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("노인 기기가 WebSocket으로 발화를 보내면 STT·음성·답변을 받고, 대화 이력이 같은 turnId로 남는다")
    void voiceTurn() throws Exception {
        // given: 보호자가 노인을 등록하고, 노인 기기가 로그인해 대화를 시작
        String caregiverToken = signUpAndLogin();
        Integer elderlyId = registerElderly(caregiverToken);
        String elderlyToken = deviceLogin(issueDeviceCode(caregiverToken, elderlyId));
        Long conversationId = startConversation(elderlyToken);

        // given: AI 서버는 다른 스레드에서 응답을 흘려보낸다 (gRPC 콜백 스레드와 같은 조건)
        AtomicReference<DialogueRequest> sentToAi = new AtomicReference<>();
        willAnswer(invocation -> {
            sentToAi.set(invocation.getArgument(0));
            DialogueListener listener = invocation.getArgument(1);
            CompletableFuture.runAsync(() -> {
                listener.onTranscript("밥 먹었어");
                listener.onAudioChunk("voice-1".getBytes(StandardCharsets.UTF_8), 0);
                listener.onAudioChunk("voice-2".getBytes(StandardCharsets.UTF_8), 1);
                listener.onCompleted("잘하셨어요");
            });
            return null;
        }).given(dialogueClient).converse(any(), any());
        RecordingClient client = connect(elderlyToken, conversationId);

        // when
        client.session.sendMessage(new TextMessage("""
                {"type":"TURN_START","audioFormat":"wav"}
                """));
        client.session.sendMessage(new BinaryMessage(new byte[AUDIO_FRAME_BYTES]));
        client.session.sendMessage(new BinaryMessage(new byte[100]));
        client.session.sendMessage(new TextMessage("""
                {"type":"TURN_END"}
                """));

        // then: 앱이 받는 프레임
        assertThat(client.next()).isEqualTo("{\"type\":\"TRANSCRIPT\",\"text\":\"밥 먹었어\"}");
        assertThat(client.next()).isEqualTo("binary:voice-1");
        assertThat(client.next()).isEqualTo("binary:voice-2");
        assertThat(client.next()).isEqualTo("{\"type\":\"COMPLETED\",\"text\":\"잘하셨어요\"}");

        // then: AI 서버로 간 요청
        assertThat(sentToAi.get().audio()).hasSize(AUDIO_FRAME_BYTES + 100);
        assertThat(sentToAi.get().subjectId()).isEqualTo(
                elderlyRepository.findById(elderlyId.longValue()).orElseThrow().getSubjectId().toString());
        assertThat(sentToAi.get().conversationId()).isEqualTo(conversationId);

        // then: 대화 이력과 상태
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
        assertThat(messages).extracting(Message::getSender).containsExactly(MessageSender.ELDERLY, MessageSender.AI);
        assertThat(messages).extracting(Message::getTurnId).containsOnly(sentToAi.get().turnId());
        assertThat(conversationRepository.findById(conversationId)).get()
                .extracting(conversation -> conversation.getState()).isEqualTo(ConversationState.WAITING);
        client.session.close();
    }

    @Test
    @DisplayName("두 번째 턴에는 같은 대화의 앞선 턴(발화·답변)을 맥락으로 실어 AI 서버에 보낸다")
    void voiceTurn_carriesConversationContext() throws Exception {
        // given
        String caregiverToken = signUpAndLogin();
        Integer elderlyId = registerElderly(caregiverToken);
        String elderlyToken = deviceLogin(issueDeviceCode(caregiverToken, elderlyId));
        Long conversationId = startConversation(elderlyToken);
        List<DialogueRequest> sentToAi = new CopyOnWriteArrayList<>();
        willAnswer(invocation -> {
            sentToAi.add(invocation.getArgument(0));
            DialogueListener listener = invocation.getArgument(1);
            CompletableFuture.runAsync(() -> {
                listener.onTranscript("밥 먹었어");
                listener.onCompleted("잘하셨어요");
            });
            return null;
        }).given(dialogueClient).converse(any(), any());
        RecordingClient client = connect(elderlyToken, conversationId);

        // when
        sendTurn(client);
        sendTurn(client);

        // then
        assertThat(sentToAi).hasSize(2);
        assertThat(sentToAi.get(0).history()).isEmpty();
        assertThat(sentToAi.get(1).history()).containsExactly(
                new DialogueHistoryTurn(sentToAi.get(0).turnId(), "밥 먹었어", "잘하셨어요"));
        client.session.close();
    }

    private void sendTurn(RecordingClient client) throws Exception {
        client.session.sendMessage(new TextMessage("""
                {"type":"TURN_START","audioFormat":"wav"}
                """));
        client.session.sendMessage(new BinaryMessage(new byte[100]));
        client.session.sendMessage(new TextMessage("""
                {"type":"TURN_END"}
                """));
        String frame;
        do {
            frame = client.next();
        } while (!frame.contains("\"type\":\"COMPLETED\""));
    }

    @Test
    @DisplayName("보호자 토큰으로 연결해 턴을 보내면 ERROR 프레임으로 거절한다")
    void voiceTurn_caregiverRejected() throws Exception {
        // given
        String caregiverToken = signUpAndLogin();
        RecordingClient client = connect(caregiverToken, 1L);

        // when
        client.session.sendMessage(new TextMessage("""
                {"type":"TURN_START","audioFormat":"wav"}
                """));
        client.session.sendMessage(new BinaryMessage(new byte[10]));
        client.session.sendMessage(new TextMessage("""
                {"type":"TURN_END"}
                """));

        // then
        assertThat(client.next()).contains("\"type\":\"ERROR\"").contains("\"code\":\"E001\"");
        client.session.close();
    }

    @Test
    @DisplayName("토큰 없이는 WebSocket 핸드셰이크가 거절된다")
    void handshake_withoutToken() {
        // when & then
        assertThatThrownBy(() -> new StandardWebSocketClient()
                .execute(new RecordingClient(), new WebSocketHttpHeaders(), voiceUri(1L))
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    private RecordingClient connect(String token, Long conversationId) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        RecordingClient client = new RecordingClient();
        client.session = new StandardWebSocketClient()
                .execute(client, headers, voiceUri(conversationId))
                .get(5, TimeUnit.SECONDS);
        return client;
    }

    private URI voiceUri(Long conversationId) {
        return URI.create("ws://localhost:" + port + "/ws/v1/conversations/" + conversationId + "/voice");
    }

    private String signUpAndLogin() {
        String email = "caregiver-" + UUID.randomUUID() + "@wilson.com";
        post("/api/v1/members", null, """
                {"email":"%s","password":"raw-password","name":"보호자","memberType":"CAREGIVER"}
                """.formatted(email));
        return JsonPath.read(post("/api/v1/auth/login", null, """
                {"email":"%s","password":"raw-password"}
                """.formatted(email)), "$.data.accessToken");
    }

    private Integer registerElderly(String token) {
        return JsonPath.read(post("/api/v1/elderlies", token, """
                {"name":"김철수","birth":"1945-03-11","gender":"MALE","relationship":"FATHER","legalBasis":"FAMILY_DELEGATION","consents":[{"type":"SENSITIVE_INFO","agreed":true,"version":1}]}
                """), "$.data.elderlyId");
    }

    private String issueDeviceCode(String token, Integer elderlyId) {
        return JsonPath.read(post("/api/v1/elderlies/" + elderlyId + "/device-code", token, "{}"),
                "$.data.deviceCode");
    }

    private String deviceLogin(String code) {
        return JsonPath.read(post("/api/v1/auth/device-login", null, """
                {"deviceCode":"%s"}
                """.formatted(code)), "$.data.accessToken");
    }

    private Long startConversation(String token) {
        Integer id = JsonPath.read(post("/api/v1/conversations", token, """
                {"conversationType":"DAILY"}
                """), "$.data.conversationId");
        return id.longValue();
    }

    private String post(String path, String token, String body) {
        RestClient.RequestBodySpec request = restClient.post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.body(body).retrieve().body(String.class);
    }

    /** 서버가 보낸 프레임을 순서대로 기록하는 테스트용 앱 클라이언트. */
    static class RecordingClient extends AbstractWebSocketHandler {

        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            received.add(message.getPayload());
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            ByteBuffer payload = message.getPayload();
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            received.add("binary:" + new String(bytes, StandardCharsets.UTF_8));
        }

        String next() throws InterruptedException {
            String frame = received.poll(5, TimeUnit.SECONDS);
            assertThat(frame).as("5초 안에 서버 프레임을 받아야 한다").isNotNull();
            return frame;
        }
    }
}
