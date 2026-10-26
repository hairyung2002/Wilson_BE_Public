package com.piuda.wilson.infrastructure.grpc;

import com.google.protobuf.ByteString;
import com.piuda.wilson.infrastructure.ai.DialogueClient;
import com.piuda.wilson.infrastructure.ai.DialogueFailure;
import com.piuda.wilson.infrastructure.ai.DialogueFailureStage;
import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import com.piuda.wilson.infrastructure.ai.DialogueListener;
import com.piuda.wilson.infrastructure.ai.DialogueRequest;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import com.piuda.wilson.infrastructure.ai.SpeechRequest;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.AudioRequest;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.DialogueServiceGrpc;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.EmergencyFrame;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.ErrorFrame;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.HistoryTurn;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.SpeakRequest;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.StreamResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 서버 {@code DialogueService} gRPC 어댑터 ({@code Converse}·{@code Speak}, docs/ai-integration-contract.md 1장).
 *
 * <p>첫 프레임 수신 전 실패만 지수 백오프로 재시도한다. 이미 음성 조각이 흘러간 뒤 재시도하면
 * 앱에서 같은 답변이 두 번 재생되기 때문이다. gRPC 예외는 밖으로 던지지 않고 {@link DialogueFailure}로 변환한다.
 * AI가 아직 만들지 않은 RPC({@code UNIMPLEMENTED})는 재시도하지 않고, Circuit Breaker 실패로도 세지 않는다.</p>
 */
@Slf4j
public class GrpcDialogueClient implements DialogueClient {

    private final DialogueServiceGrpc.DialogueServiceStub stub;
    private final CircuitBreaker circuitBreaker;
    private final ScheduledExecutorService scheduler;
    private final GrpcDialogueProperties properties;

    public GrpcDialogueClient(ManagedChannel channel, CircuitBreaker circuitBreaker,
                              ScheduledExecutorService scheduler, GrpcDialogueProperties properties) {
        this.stub = DialogueServiceGrpc.newStub(channel);
        this.circuitBreaker = circuitBreaker;
        this.scheduler = scheduler;
        this.properties = properties;
    }

    @Override
    public void converse(DialogueRequest request, DialogueListener listener) {
        AudioRequest audioRequest = toAudioRequest(request);
        attempt(new Call("Converse", request.turnId(),
                (callStub, observer) -> callStub.converse(audioRequest, observer)), listener, 1);
    }

    @Override
    public void speak(SpeechRequest request, DialogueListener listener) {
        SpeakRequest speakRequest = toSpeakRequest(request);
        attempt(new Call("Speak", request.turnId(),
                (callStub, observer) -> callStub.speak(speakRequest, observer)), listener, 1);
    }

    private void attempt(Call call, DialogueListener listener, int attemptNumber) {
        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("AI dialogue circuit open: rpc={}, turnId={}", call.rpc(), call.turnId());
            listener.onFailed(DialogueFailure.unavailable());
            return;
        }
        call.start().accept(stub.withDeadlineAfter(properties.streamDeadline().toMillis(), TimeUnit.MILLISECONDS),
                new AttemptObserver(call, listener, attemptNumber));
    }

    private void retryOrFail(Call call, DialogueListener listener, int attemptNumber) {
        if (attemptNumber >= properties.maxAttempts()) {
            log.error("AI dialogue failed after {} attempts: rpc={}, turnId={}", attemptNumber, call.rpc(),
                    call.turnId());
            listener.onFailed(DialogueFailure.unavailable());
            return;
        }
        long backoffMillis = properties.initialBackoff().toMillis() << (attemptNumber - 1);
        scheduler.schedule(() -> attempt(call, listener, attemptNumber + 1), backoffMillis, TimeUnit.MILLISECONDS);
    }

    private AudioRequest toAudioRequest(DialogueRequest request) {
        return AudioRequest.newBuilder()
                .setAudioPayload(ByteString.copyFrom(request.audio()))
                .setTraceId(request.traceId())
                .setTargetUserId(request.subjectId())
                .setSessionId(String.valueOf(request.conversationId()))
                .setTurnId(request.turnId())
                .setAudioFormat(request.audioFormat())
                .setLanguageCode(properties.languageCode())
                .addAllHistory(request.history().stream().map(GrpcDialogueClient::toHistoryTurn).toList())
                .setAddressTerm(request.addressTerm())
                .build();
    }

    private SpeakRequest toSpeakRequest(SpeechRequest request) {
        return SpeakRequest.newBuilder()
                .setTraceId(request.traceId())
                .setTargetUserId(request.subjectId())
                .setSessionId(String.valueOf(request.conversationId()))
                .setTurnId(request.turnId())
                .setText(request.text())
                .setLanguageCode(properties.languageCode())
                .build();
    }

    private static HistoryTurn toHistoryTurn(DialogueHistoryTurn turn) {
        return HistoryTurn.newBuilder()
                .setTurnId(turn.turnId())
                .setSttText(turn.sttText())
                .setLlmResponseText(turn.llmResponseText())
                .build();
    }

    private static DialogueFailure toFailure(ErrorFrame error) {
        DialogueFailureStage stage = switch (error.getErrorStage()) {
            case ERROR_STAGE_STT -> DialogueFailureStage.STT;
            case ERROR_STAGE_LLM -> DialogueFailureStage.LLM;
            case ERROR_STAGE_TTS -> DialogueFailureStage.TTS;
            default -> DialogueFailureStage.UNSPECIFIED;
        };
        String text = error.getFullLlmResponseText().isEmpty() ? null : error.getFullLlmResponseText();
        return new DialogueFailure(stage, error.getRecoverable(), text);
    }

    /** 모르는 값이 와도 응급 신호 자체는 놓치지 않도록 기타로 본다. */
    private static EmergencyCategory toCategory(EmergencyFrame emergency) {
        return switch (emergency.getCategory()) {
            case EMERGENCY_CATEGORY_FALL -> EmergencyCategory.FALL;
            case EMERGENCY_CATEGORY_PAIN -> EmergencyCategory.PAIN;
            case EMERGENCY_CATEGORY_BREATHING -> EmergencyCategory.BREATHING;
            case EMERGENCY_CATEGORY_HELP_REQUEST -> EmergencyCategory.HELP_REQUEST;
            default -> EmergencyCategory.OTHER;
        };
    }

    /** 한 RPC 호출. 재시도할 때 같은 요청으로 다시 시작한다. */
    private record Call(String rpc, String turnId,
                        BiConsumer<DialogueServiceGrpc.DialogueServiceStub, StreamObserver<StreamResponse>> start) {
    }

    /** 한 번의 시도에 대한 스트림 수신기. gRPC는 한 호출의 콜백을 순차적으로 호출한다. */
    private class AttemptObserver implements ClientResponseObserver<Object, StreamResponse> {

        private final Call call;
        private final DialogueListener listener;
        private final int attemptNumber;
        private final long startedAtNanos = System.nanoTime();
        private final AtomicBoolean firstFrameReceived = new AtomicBoolean();
        private final AtomicBoolean firstFrameTimedOut = new AtomicBoolean();
        private boolean terminated;
        private ScheduledFuture<?> firstFrameTimer;

        AttemptObserver(Call call, DialogueListener listener, int attemptNumber) {
            this.call = call;
            this.listener = listener;
            this.attemptNumber = attemptNumber;
        }

        @Override
        public void beforeStart(ClientCallStreamObserver<Object> requestStream) {
            firstFrameTimer = scheduler.schedule(() -> {
                if (!firstFrameReceived.get() && firstFrameTimedOut.compareAndSet(false, true)) {
                    requestStream.cancel("first frame timeout", null);
                }
            }, properties.firstFrameTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void onNext(StreamResponse response) {
            if (terminated || firstFrameTimedOut.get()) {
                return;
            }
            if (firstFrameReceived.compareAndSet(false, true)) {
                firstFrameTimer.cancel(false);
            }
            switch (response.getPayloadCase()) {
                case METADATA -> listener.onTranscript(response.getMetadata().getSttText());
                case EMERGENCY -> listener.onEmergency(toCategory(response.getEmergency()));
                case AUDIO_CHUNK -> listener.onAudioChunk(
                        response.getAudioChunk().getData().toByteArray(), response.getAudioChunk().getSequence());
                case COMPLETION -> terminate(() ->
                        listener.onCompleted(response.getCompletion().getFullLlmResponseText()));
                case ERROR -> terminate(() -> {
                    log.warn("AI dialogue error frame: rpc={}, turnId={}, stage={}",
                            call.rpc(), call.turnId(), response.getError().getErrorStage());
                    listener.onFailed(toFailure(response.getError()));
                });
                default -> log.warn("AI dialogue unknown frame ignored: rpc={}, turnId={}", call.rpc(), call.turnId());
            }
        }

        @Override
        public void onError(Throwable t) {
            firstFrameTimer.cancel(false);
            if (terminated) {
                return;
            }
            terminated = true;
            Status status = Status.fromThrowable(t);
            if (status.getCode() == Status.Code.UNIMPLEMENTED) {
                circuitBreaker.releasePermission();
                log.warn("AI dialogue rpc not implemented: rpc={}, turnId={}", call.rpc(), call.turnId());
                listener.onFailed(DialogueFailure.unavailable());
                return;
            }
            circuitBreaker.onError(elapsedNanos(), TimeUnit.NANOSECONDS, t);
            log.warn("AI dialogue call failed: rpc={}, turnId={}, attempt={}, status={}, firstFrameTimeout={}",
                    call.rpc(), call.turnId(), attemptNumber, status.getCode(), firstFrameTimedOut.get());

            if (firstFrameReceived.get()) {
                listener.onFailed(DialogueFailure.unavailable());
                return;
            }
            retryOrFail(call, listener, attemptNumber);
        }

        @Override
        public void onCompleted() {
            firstFrameTimer.cancel(false);
            if (terminated) {
                return;
            }
            terminated = true;
            log.error("AI dialogue stream closed without completion frame: rpc={}, turnId={}", call.rpc(),
                    call.turnId());
            circuitBreaker.onSuccess(elapsedNanos(), TimeUnit.NANOSECONDS);
            listener.onFailed(DialogueFailure.unavailable());
        }

        private void terminate(Runnable notifyListener) {
            terminated = true;
            circuitBreaker.onSuccess(elapsedNanos(), TimeUnit.NANOSECONDS);
            notifyListener.run();
        }

        private long elapsedNanos() {
            return System.nanoTime() - startedAtNanos;
        }
    }
}
