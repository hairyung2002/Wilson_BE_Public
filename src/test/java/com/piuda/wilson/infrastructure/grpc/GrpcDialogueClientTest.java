package com.piuda.wilson.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.piuda.wilson.infrastructure.ai.DialogueFailure;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import com.piuda.wilson.infrastructure.ai.DialogueFailureStage;
import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import com.piuda.wilson.infrastructure.ai.DialogueListener;
import com.piuda.wilson.infrastructure.ai.DialogueRequest;
import com.piuda.wilson.infrastructure.ai.SpeechRequest;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.AudioChunk;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.AudioRequest;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.CompletionFrame;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.DialogueServiceGrpc;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.EmergencyFrame;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.ErrorFrame;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.ErrorStage;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.MetadataFrame;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.SpeakRequest;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.StreamResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GrpcDialogueClientTest {

    private static final GrpcDialogueProperties PROPERTIES = new GrpcDialogueProperties(
            "in-process", "round_robin", Duration.ofMillis(300), Duration.ofSeconds(3), 3, Duration.ofMillis(10), "ko-KR");
    private static final String SUBJECT_ID = "3b1f0c9e-6a1d-4c55-9a8e-2f4b7d0c1e23";

    private final FakeDialogueService fakeService = new FakeDialogueService();
    private Server server;
    private ManagedChannel channel;
    private ScheduledExecutorService scheduler;
    private CircuitBreaker circuitBreaker;
    private GrpcDialogueClient client;

    @BeforeEach
    void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(fakeService).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        circuitBreaker = CircuitBreaker.ofDefaults("ai-dialogue-test");
        client = new GrpcDialogueClient(channel, circuitBreaker, scheduler, PROPERTIES);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
        scheduler.shutdownNow();
    }

    private static DialogueRequest request() {
        return new DialogueRequest("audio".getBytes(StandardCharsets.UTF_8), "trace-1", SUBJECT_ID, 100L, "turn-1", "wav",
                List.of(new DialogueHistoryTurn("turn-0", "오늘 날씨 좋네", "산책하기 좋은 날이에요")), "할머니");
    }

    private static SpeechRequest speechRequest() {
        return new SpeechRequest("trace-2", SUBJECT_ID, 100L, "turn-g", "할머니, 오늘 점심은 뭐 드셨어요?");
    }

    private static StreamResponse emergency(EmergencyFrame.Builder frame) {
        return StreamResponse.newBuilder().setEmergency(frame).build();
    }

    private static StreamResponse metadata(String sttText) {
        return StreamResponse.newBuilder().setMetadata(MetadataFrame.newBuilder().setSttText(sttText)).build();
    }

    private static StreamResponse chunk(String data, int sequence) {
        return StreamResponse.newBuilder().setAudioChunk(AudioChunk.newBuilder()
                .setData(ByteString.copyFromUtf8(data)).setSequence(sequence)).build();
    }

    private static StreamResponse completion(String text) {
        return StreamResponse.newBuilder()
                .setCompletion(CompletionFrame.newBuilder().setFullLlmResponseText(text)).build();
    }

    private static final BiConsumer<AudioRequest, StreamObserver<StreamResponse>> SUCCESS = (request, observer) -> {
        observer.onNext(metadata("밥 먹었어"));
        observer.onNext(chunk("a", 0));
        observer.onNext(chunk("b", 1));
        observer.onNext(completion("잘하셨어요"));
        observer.onCompleted();
    };

    private static final BiConsumer<AudioRequest, StreamObserver<StreamResponse>> UNAVAILABLE =
            (request, observer) -> observer.onError(Status.UNAVAILABLE.asRuntimeException());

    @Test
    @DisplayName("요청 식별자를 그대로 전달하고, 스트림 프레임을 순서대로 이벤트로 변환한다")
    void converse_success() {
        // given
        fakeService.behaviors.add(SUCCESS);
        RecordingListener listener = new RecordingListener();

        // when
        client.converse(request(), listener);

        // then
        listener.awaitTerminal();
        assertThat(listener.events).containsExactly(
                "transcript:밥 먹었어", "chunk:0:a", "chunk:1:b", "completed:잘하셨어요");
        AudioRequest sent = fakeService.requests.get(0);
        assertThat(sent.getTargetUserId()).as("AI에는 노인 PK가 아닌 가명 식별자를 보낸다").isEqualTo(SUBJECT_ID);
        assertThat(sent.getSessionId()).isEqualTo("100");
        assertThat(sent.getTurnId()).isEqualTo("turn-1");
        assertThat(sent.getTraceId()).isEqualTo("trace-1");
        assertThat(sent.getAudioFormat()).isEqualTo("wav");
        assertThat(sent.getLanguageCode()).isEqualTo("ko-KR");
        assertThat(sent.getAudioPayload().toStringUtf8()).isEqualTo("audio");
        assertThat(sent.getHistoryList()).singleElement().satisfies(turn -> {
            assertThat(turn.getTurnId()).isEqualTo("turn-0");
            assertThat(turn.getSttText()).isEqualTo("오늘 날씨 좋네");
            assertThat(turn.getLlmResponseText()).isEqualTo("산책하기 좋은 날이에요");
        });
        assertThat(sent.getAddressTerm()).isEqualTo("할머니");
    }

    @Test
    @DisplayName("EmergencyFrame을 받으면 응급 종류를 전달하고, 이어지는 답변도 그대로 전달한다")
    void converse_emergencyFrame() {
        // given
        fakeService.behaviors.add((request, observer) -> {
            observer.onNext(metadata("아이고 넘어졌어"));
            observer.onNext(emergency(EmergencyFrame.newBuilder()
                    .setCategory(com.piuda.wilson.infrastructure.grpc.dialogue.v1.EmergencyCategory
                            .EMERGENCY_CATEGORY_FALL)));
            observer.onNext(completion("많이 놀라셨죠"));
            observer.onCompleted();
        });
        RecordingListener listener = new RecordingListener();

        // when
        client.converse(request(), listener);

        // then
        listener.awaitTerminal();
        assertThat(listener.events).containsExactly(
                "transcript:아이고 넘어졌어", "emergency:FALL", "completed:많이 놀라셨죠");
    }

    @Test
    @DisplayName("응급 종류가 비어 있거나 모르는 값이면 기타(OTHER)로 전달한다 (응급 신호 자체는 놓치지 않는다)")
    void converse_emergencyUnspecified() {
        // given
        fakeService.behaviors.add((request, observer) -> {
            observer.onNext(metadata("도와줘"));
            observer.onNext(emergency(EmergencyFrame.newBuilder()));
            observer.onNext(completion("곧 연락드릴게요"));
            observer.onCompleted();
        });
        RecordingListener listener = new RecordingListener();

        // when
        client.converse(request(), listener);

        // then
        listener.awaitTerminal();
        assertThat(listener.events).contains("emergency:OTHER");
    }

    @Test
    @DisplayName("Speak는 BE가 정한 문장을 그대로 보내고, 음성 조각과 완료를 이벤트로 전달한다")
    void speak_success() {
        // given
        fakeService.speakBehaviors.add((request, observer) -> {
            observer.onNext(chunk("a", 1));
            observer.onNext(completion(request.getText()));
            observer.onCompleted();
        });
        RecordingListener listener = new RecordingListener();

        // when
        client.speak(speechRequest(), listener);

        // then
        listener.awaitTerminal();
        assertThat(listener.events).containsExactly("chunk:1:a", "completed:할머니, 오늘 점심은 뭐 드셨어요?");
        SpeakRequest sent = fakeService.speakRequests.get(0);
        assertThat(sent.getTargetUserId()).isEqualTo(SUBJECT_ID);
        assertThat(sent.getSessionId()).isEqualTo("100");
        assertThat(sent.getTurnId()).isEqualTo("turn-g");
        assertThat(sent.getTraceId()).isEqualTo("trace-2");
        assertThat(sent.getText()).isEqualTo("할머니, 오늘 점심은 뭐 드셨어요?");
        assertThat(sent.getLanguageCode()).isEqualTo("ko-KR");
    }

    @Test
    @DisplayName("AI가 Speak를 아직 지원하지 않으면(UNIMPLEMENTED) 재시도 없이 실패를 전달하고, 장애로 세지 않는다")
    void speak_unimplemented() {
        // given: Speak behavior를 넣지 않으면 기본 구현(UNIMPLEMENTED)이 응답한다
        RecordingListener listener = new RecordingListener();

        // when
        client.speak(speechRequest(), listener);

        // then
        listener.awaitTerminal();
        assertThat(listener.failure.stage()).isEqualTo(DialogueFailureStage.UNAVAILABLE);
        assertThat(fakeService.speakCalls.get()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls())
                .as("미지원 기능 때문에 대화(Converse)용 Circuit Breaker가 열리면 안 된다").isZero();
    }

    @Test
    @DisplayName("ErrorFrame을 받으면 실패 단계·복구 가능 여부·완성된 답변 텍스트를 전달한다")
    void converse_errorFrame() {
        // given
        fakeService.behaviors.add((request, observer) -> {
            observer.onNext(metadata("밥 먹었어"));
            observer.onNext(chunk("a", 0));
            observer.onNext(StreamResponse.newBuilder().setError(ErrorFrame.newBuilder()
                    .setErrorStage(ErrorStage.ERROR_STAGE_TTS)
                    .setRecoverable(true)
                    .setFullLlmResponseText("잘하셨어요")).build());
            observer.onCompleted();
        });
        RecordingListener listener = new RecordingListener();

        // when
        client.converse(request(), listener);

        // then
        listener.awaitTerminal();
        assertThat(listener.failure.stage()).isEqualTo(DialogueFailureStage.TTS);
        assertThat(listener.failure.recoverable()).isTrue();
        assertThat(listener.failure.fullLlmResponseText()).isEqualTo("잘하셨어요");
        assertThat(fakeService.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("첫 프레임 전 연결 실패는 재시도해서 성공하면 정상 결과를 전달한다")
    void converse_retryBeforeFirstFrame() {
        // given
        fakeService.behaviors.add(UNAVAILABLE);
        fakeService.behaviors.add(UNAVAILABLE);
        fakeService.behaviors.add(SUCCESS);
        RecordingListener listener = new RecordingListener();

        // when
        client.converse(request(), listener);

        // then
        listener.awaitTerminal();
        assertThat(listener.events).last().isEqualTo("completed:잘하셨어요");
        assertThat(fakeService.calls.get()).isEqualTo(3);
        assertThat(fakeService.requests).extracting(AudioRequest::getTurnId).containsOnly("turn-1");
    }

    @Test
    @DisplayName("재시도를 모두 실패하면 gRPC 예외 대신 UNAVAILABLE 실패를 전달한다")
    void converse_retryExhausted() {
        // given
        RecordingListener listener = new RecordingListener();

        // when
        client.converse(request(), listener);

        // then
        listener.awaitTerminal();
        assertThat(listener.failure.stage()).isEqualTo(DialogueFailureStage.UNAVAILABLE);
        assertThat(listener.failure.recoverable()).isFalse();
        assertThat(fakeService.calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("첫 프레임 이후 끊기면 중복 재생을 막기 위해 재시도하지 않는다")
    void converse_noRetryAfterFirstFrame() {
        // given
        fakeService.behaviors.add((request, observer) -> {
            observer.onNext(metadata("밥 먹었어"));
            observer.onNext(chunk("a", 0));
            observer.onError(Status.INTERNAL.asRuntimeException());
        });
        RecordingListener listener = new RecordingListener();

        // when
        client.converse(request(), listener);

        // then
        listener.awaitTerminal();
        assertThat(listener.events).containsExactly("transcript:밥 먹었어", "chunk:0:a", "failed:UNAVAILABLE");
        assertThat(fakeService.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("첫 프레임이 제한 시간 안에 오지 않으면 호출을 취소하고 재시도한다")
    void converse_firstFrameTimeout() {
        // given
        BiConsumer<AudioRequest, StreamObserver<StreamResponse>> silent = (request, observer) -> { };
        fakeService.behaviors.add(silent);
        fakeService.behaviors.add(SUCCESS);
        RecordingListener listener = new RecordingListener();

        // when
        client.converse(request(), listener);

        // then
        listener.awaitTerminal();
        assertThat(listener.events).last().isEqualTo("completed:잘하셨어요");
        assertThat(fakeService.calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Circuit Breaker가 열려 있으면 AI 서버를 호출하지 않고 즉시 실패를 전달한다")
    void converse_circuitOpen() {
        // given
        circuitBreaker.transitionToOpenState();
        RecordingListener listener = new RecordingListener();

        // when
        client.converse(request(), listener);

        // then
        listener.awaitTerminal();
        assertThat(listener.failure.stage()).isEqualTo(DialogueFailureStage.UNAVAILABLE);
        assertThat(fakeService.calls.get()).isZero();
    }

    static class FakeDialogueService extends DialogueServiceGrpc.DialogueServiceImplBase {

        final Deque<BiConsumer<AudioRequest, StreamObserver<StreamResponse>>> behaviors = new ArrayDeque<>();
        final List<AudioRequest> requests = new CopyOnWriteArrayList<>();
        final AtomicInteger calls = new AtomicInteger();
        final Deque<BiConsumer<SpeakRequest, StreamObserver<StreamResponse>>> speakBehaviors = new ArrayDeque<>();
        final List<SpeakRequest> speakRequests = new CopyOnWriteArrayList<>();
        final AtomicInteger speakCalls = new AtomicInteger();

        @Override
        public void converse(AudioRequest request, StreamObserver<StreamResponse> responseObserver) {
            calls.incrementAndGet();
            requests.add(request);
            BiConsumer<AudioRequest, StreamObserver<StreamResponse>> behavior = behaviors.poll();
            (behavior != null ? behavior : UNAVAILABLE).accept(request, responseObserver);
        }

        @Override
        public void speak(SpeakRequest request, StreamObserver<StreamResponse> responseObserver) {
            speakCalls.incrementAndGet();
            speakRequests.add(request);
            BiConsumer<SpeakRequest, StreamObserver<StreamResponse>> behavior = speakBehaviors.poll();
            if (behavior == null) {
                super.speak(request, responseObserver);
                return;
            }
            behavior.accept(request, responseObserver);
        }
    }

    static class RecordingListener implements DialogueListener {

        final List<String> events = new CopyOnWriteArrayList<>();
        final CountDownLatch terminal = new CountDownLatch(1);
        final AtomicInteger terminalCount = new AtomicInteger();
        volatile DialogueFailure failure;

        @Override
        public void onTranscript(String sttText) {
            events.add("transcript:" + sttText);
        }

        @Override
        public void onAudioChunk(byte[] data, int sequence) {
            events.add("chunk:" + sequence + ":" + new String(data, StandardCharsets.UTF_8));
        }

        @Override
        public void onCompleted(String fullLlmResponseText) {
            events.add("completed:" + fullLlmResponseText);
            terminalCount.incrementAndGet();
            terminal.countDown();
        }

        @Override
        public void onEmergency(EmergencyCategory category) {
            events.add("emergency:" + category);
        }

        @Override
        public void onFailed(DialogueFailure failure) {
            events.add("failed:" + failure.stage());
            this.failure = failure;
            terminalCount.incrementAndGet();
            terminal.countDown();
        }

        void awaitTerminal() {
            try {
                assertThat(terminal.await(5, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertThat(terminalCount.get()).as("종료 이벤트는 정확히 한 번").isEqualTo(1);
        }
    }
}
