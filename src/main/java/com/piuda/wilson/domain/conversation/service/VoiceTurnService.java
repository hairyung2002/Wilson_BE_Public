package com.piuda.wilson.domain.conversation.service;

import com.piuda.wilson.domain.emergency.service.EmergencyService;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.infrastructure.ai.AiEventPublisher;
import com.piuda.wilson.infrastructure.ai.DialogueClient;
import com.piuda.wilson.infrastructure.ai.DialogueFailure;
import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import com.piuda.wilson.infrastructure.ai.DialogueListener;
import com.piuda.wilson.infrastructure.ai.DialogueRequest;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import com.piuda.wilson.infrastructure.ai.SpeechRequest;
import com.piuda.wilson.infrastructure.ai.TurnAnalysisEvent;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 노인 발화 1건(음성 턴)과 선제 대화 첫 안부를 처리한다.
 *
 * <p>AI 응답 스트림을 받아 STT 원문과 AI 답변을 대화 이력에 남기고, 음성 조각은 앱으로 그대로 흘려보낸다.
 * 답변을 보낸 뒤 그 턴을 분석 워커로 넘긴다 (결정 J1, 대화는 분석을 기다리지 않는다).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTurnService {

    private final ConversationTurnRecorder recorder;
    private final DialogueClient dialogueClient;
    private final ConversationContextService contextService;
    private final ProactiveService proactiveService;
    private final EmergencyService emergencyService;
    private final AiEventPublisher aiEventPublisher;
    private final Clock clock;

    /**
     * 선제 대화의 첫 안부를 AI 목소리로 들려준다 (WebSocket PROACTIVE_START, 결정 J4).
     * 시작할 수 없으면 {@link CustomException}을 던진다. AI가 읽지 못해도 문장은 COMPLETED로 보내
     * 앱이 기기 음성으로 읽게 한다.
     */
    public void startProactiveGreeting(Long memberId, Long conversationId, VoiceTurnSink sink) {
        GreetingTurn turn = recorder.beginGreeting(memberId, conversationId);
        contextService.appendTurn(conversationId, new DialogueHistoryTurn(turn.turnId(), "", turn.text()));
        log.info("Proactive greeting started: conversationId={}, turnId={}, traceId={}",
                conversationId, turn.turnId(), turn.traceId());

        SpeechRequest request = new SpeechRequest(turn.traceId(), turn.subjectId(), conversationId, turn.turnId(),
                turn.text());
        dialogueClient.speak(request, new GreetingListener(turn, sink));
    }

    /** 턴을 시작할 수 없으면 {@link CustomException}을 던진다. 이후 결과는 모두 sink로 전달된다. */
    public void handleTurn(Long memberId, Long conversationId, byte[] audio, String audioFormat,
                           VoiceTurnSink sink) {
        if (audio == null || audio.length == 0) {
            throw new CustomException(ErrorCode.EMPTY_AUDIO);
        }
        VoiceTurn turn = recorder.beginTurn(memberId, conversationId);
        List<DialogueHistoryTurn> history = contextService.loadRecentTurns(conversationId);
        log.info("Voice turn started: conversationId={}, turnId={}, traceId={}, audioBytes={}, historyTurns={}",
                conversationId, turn.turnId(), turn.traceId(), audio.length, history.size());

        DialogueRequest request = new DialogueRequest(audio, turn.traceId(), turn.subjectId(), conversationId,
                turn.turnId(), audioFormat, history, turn.addressTerm());
        dialogueClient.converse(request, new TurnListener(turn, audio, audioFormat, sink));
    }

    private class TurnListener implements DialogueListener {

        private final VoiceTurn turn;
        private final byte[] audio;
        private final String audioFormat;
        private final VoiceTurnSink sink;
        private final AtomicBoolean aborted = new AtomicBoolean();
        private volatile String sttText;

        TurnListener(VoiceTurn turn, byte[] audio, String audioFormat, VoiceTurnSink sink) {
            this.turn = turn;
            this.audio = audio;
            this.audioFormat = audioFormat;
            this.sink = sink;
        }

        @Override
        public void onTranscript(String sttText) {
            guarded(() -> {
                recorder.recordElderlyUtterance(turn.conversationId(), turn.turnId(), sttText);
                proactiveService.markResponded(turn.conversationId());
                this.sttText = sttText;
                sink.sendTranscript(sttText);
            });
        }

        /**
         * 응급 기록·보호자 알림이 실패해도 턴은 끊지 않고, 노인 기기에는 안내 음성이 나가도록 EMERGENCY를 보낸다.
         */
        @Override
        public void onEmergency(EmergencyCategory category) {
            if (aborted.get()) {
                return;
            }
            try {
                emergencyService.report(turn.conversationId(), turn.turnId(), category);
            } catch (RuntimeException e) {
                log.error("Emergency report failed: turnId={}, category={}", turn.turnId(), category, e);
            }
            sink.sendEmergency(category);
        }

        @Override
        public void onAudioChunk(byte[] data, int sequence) {
            if (!aborted.get()) {
                sink.sendAudioChunk(data, sequence);
            }
        }

        @Override
        public void onCompleted(String fullLlmResponseText) {
            guarded(() -> {
                recorder.recordAiReply(turn.conversationId(), turn.turnId(), fullLlmResponseText);
                rememberTurn(fullLlmResponseText);
                sink.sendCompleted(fullLlmResponseText);
                log.info("Voice turn completed: turnId={}", turn.turnId());
                requestAnalysis(fullLlmResponseText);
            });
        }

        @Override
        public void onFailed(DialogueFailure failure) {
            guarded(() -> {
                if (failure.fullLlmResponseText() != null) {
                    recorder.recordAiReply(turn.conversationId(), turn.turnId(), failure.fullLlmResponseText());
                    rememberTurn(failure.fullLlmResponseText());
                } else {
                    recorder.endTurnWithoutReply(turn.conversationId());
                }
                sink.sendFailed(failure);
                log.warn("Voice turn failed: turnId={}, stage={}", turn.turnId(), failure.stage());
                requestAnalysis(failure.fullLlmResponseText());
            });
        }

        /** 발화와 답변이 모두 있는 턴만 다음 턴의 맥락이 된다. */
        private void rememberTurn(String llmResponseText) {
            if (sttText != null) {
                contextService.appendTurn(turn.conversationId(),
                        new DialogueHistoryTurn(turn.turnId(), sttText, llmResponseText));
            }
        }

        /** 노인 발화가 인식된 턴만 분석한다. STT부터 실패했으면 분석할 발화가 없다. */
        private void requestAnalysis(String llmResponseText) {
            if (sttText == null) {
                return;
            }
            aiEventPublisher.publishTurnAnalysis(new TurnAnalysisEvent(turn.traceId(), turn.turnId(),
                    turn.subjectId(), turn.conversationId(), audio, audioFormat, sttText, llmResponseText,
                    clock.instant()));
        }

        /** 기록 중 오류가 나면 턴을 끝내고 앱에 실패를 한 번만 알린 뒤, 이후 이벤트는 무시한다. */
        private void guarded(Runnable step) {
            if (aborted.get()) {
                return;
            }
            try {
                step.run();
            } catch (RuntimeException e) {
                aborted.set(true);
                log.error("Voice turn recording failed: turnId={}", turn.turnId(), e);
                endTurnQuietly(turn.conversationId());
                sink.sendFailed(DialogueFailure.unavailable());
            }
        }
    }

    /**
     * 첫 안부 읽기 스트림 수신기. 문장은 이미 대화 이력에 있으므로 끝나면 턴만 닫고,
     * 성공·실패와 관계없이 BE가 정한 문장을 COMPLETED로 보낸다 (실패면 앱이 기기 음성으로 읽는다).
     */
    private class GreetingListener implements DialogueListener {

        private final GreetingTurn turn;
        private final VoiceTurnSink sink;

        GreetingListener(GreetingTurn turn, VoiceTurnSink sink) {
            this.turn = turn;
            this.sink = sink;
        }

        @Override
        public void onTranscript(String sttText) {
            log.warn("Unexpected transcript in greeting ignored: turnId={}", turn.turnId());
        }

        @Override
        public void onEmergency(EmergencyCategory category) {
            log.warn("Unexpected emergency in greeting ignored: turnId={}", turn.turnId());
        }

        @Override
        public void onAudioChunk(byte[] data, int sequence) {
            sink.sendAudioChunk(data, sequence);
        }

        @Override
        public void onCompleted(String fullLlmResponseText) {
            finish();
            log.info("Proactive greeting completed: turnId={}", turn.turnId());
        }

        @Override
        public void onFailed(DialogueFailure failure) {
            finish();
            log.warn("Proactive greeting spoken as text only: turnId={}, stage={}", turn.turnId(), failure.stage());
        }

        private void finish() {
            endTurnQuietly(turn.conversationId());
            sink.sendCompleted(turn.text());
        }
    }

    private void endTurnQuietly(Long conversationId) {
        try {
            recorder.endTurnWithoutReply(conversationId);
        } catch (RuntimeException e) {
            log.error("Failed to reset turn state: conversationId={}", conversationId, e);
        }
    }
}
