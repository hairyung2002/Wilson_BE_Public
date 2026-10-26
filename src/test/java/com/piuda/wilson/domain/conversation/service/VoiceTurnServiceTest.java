package com.piuda.wilson.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.emergency.service.EmergencyService;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.infrastructure.ai.AiEventPublisher;
import com.piuda.wilson.infrastructure.ai.DialogueClient;
import com.piuda.wilson.infrastructure.ai.DialogueFailure;
import com.piuda.wilson.infrastructure.ai.DialogueFailureStage;
import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import com.piuda.wilson.infrastructure.ai.DialogueListener;
import com.piuda.wilson.infrastructure.ai.DialogueRequest;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import com.piuda.wilson.infrastructure.ai.SpeechRequest;
import com.piuda.wilson.infrastructure.ai.TurnAnalysisEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class VoiceTurnServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CONVERSATION_ID = 100L;
    private static final VoiceTurn TURN = new VoiceTurn("subject-1", CONVERSATION_ID, "turn-1", "trace-1", "할머니");
    private static final String GREETING = "할머니, 오늘 점심은 뭐 드셨어요?";
    private static final GreetingTurn GREETING_TURN =
            new GreetingTurn("subject-1", CONVERSATION_ID, "turn-g", "trace-g", GREETING);
    private static final byte[] AUDIO = {1, 2, 3};
    private static final DialogueHistoryTurn PREVIOUS_TURN =
            new DialogueHistoryTurn("turn-0", "오늘 날씨 좋네", "산책하기 좋은 날이에요");
    private static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z");

    @Mock
    private ConversationTurnRecorder recorder;

    @Mock
    private DialogueClient dialogueClient;

    @Mock
    private ConversationContextService contextService;

    @Mock
    private ProactiveService proactiveService;

    @Mock
    private EmergencyService emergencyService;

    @Mock
    private AiEventPublisher aiEventPublisher;

    @Mock
    private VoiceTurnSink sink;

    private VoiceTurnService voiceTurnService;

    @BeforeEach
    void setUp() {
        voiceTurnService = new VoiceTurnService(recorder, dialogueClient, contextService, proactiveService,
                emergencyService, aiEventPublisher, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    }

    private TurnAnalysisEvent publishedAnalysis() {
        ArgumentCaptor<TurnAnalysisEvent> event = ArgumentCaptor.forClass(TurnAnalysisEvent.class);
        verify(aiEventPublisher).publishTurnAnalysis(event.capture());
        return event.getValue();
    }

    private DialogueListener startGreeting() {
        given(recorder.beginGreeting(MEMBER_ID, CONVERSATION_ID)).willReturn(GREETING_TURN);
        voiceTurnService.startProactiveGreeting(MEMBER_ID, CONVERSATION_ID, sink);
        ArgumentCaptor<DialogueListener> listener = ArgumentCaptor.forClass(DialogueListener.class);
        verify(dialogueClient).speak(any(), listener.capture());
        return listener.getValue();
    }

    private DialogueListener startTurn() {
        given(recorder.beginTurn(MEMBER_ID, CONVERSATION_ID)).willReturn(TURN);
        voiceTurnService.handleTurn(MEMBER_ID, CONVERSATION_ID, AUDIO, "wav", sink);
        ArgumentCaptor<DialogueListener> listener = ArgumentCaptor.forClass(DialogueListener.class);
        verify(dialogueClient).converse(any(), listener.capture());
        return listener.getValue();
    }

    @Test
    @DisplayName("턴 식별자·오디오·이전 턴 맥락을 AI 서버로 보내고, 발화·음성·답변을 저장하며 앱으로 전달한다")
    void handleTurn_success() {
        // given
        given(recorder.beginTurn(MEMBER_ID, CONVERSATION_ID)).willReturn(TURN);
        given(contextService.loadRecentTurns(CONVERSATION_ID)).willReturn(List.of(PREVIOUS_TURN));

        // when
        voiceTurnService.handleTurn(MEMBER_ID, CONVERSATION_ID, AUDIO, "wav", sink);
        ArgumentCaptor<DialogueRequest> request = ArgumentCaptor.forClass(DialogueRequest.class);
        ArgumentCaptor<DialogueListener> listenerCaptor = ArgumentCaptor.forClass(DialogueListener.class);
        verify(dialogueClient).converse(request.capture(), listenerCaptor.capture());
        DialogueListener listener = listenerCaptor.getValue();
        listener.onTranscript("밥 먹었어");
        listener.onAudioChunk(new byte[]{9}, 0);
        listener.onCompleted("잘하셨어요");

        // then
        assertThat(request.getValue().audio()).isEqualTo(AUDIO);
        assertThat(request.getValue().subjectId()).isEqualTo("subject-1");
        assertThat(request.getValue().conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(request.getValue().turnId()).isEqualTo("turn-1");
        assertThat(request.getValue().traceId()).isEqualTo("trace-1");
        assertThat(request.getValue().audioFormat()).isEqualTo("wav");
        assertThat(request.getValue().history()).containsExactly(PREVIOUS_TURN);
        assertThat(request.getValue().addressTerm()).isEqualTo("할머니");
        InOrder inOrder = inOrder(recorder, contextService, sink, aiEventPublisher);
        inOrder.verify(recorder).recordElderlyUtterance(CONVERSATION_ID, "turn-1", "밥 먹었어");
        verify(proactiveService).markResponded(CONVERSATION_ID);
        inOrder.verify(sink).sendTranscript("밥 먹었어");
        inOrder.verify(sink).sendAudioChunk(new byte[]{9}, 0);
        inOrder.verify(recorder).recordAiReply(CONVERSATION_ID, "turn-1", "잘하셨어요");
        inOrder.verify(contextService)
                .appendTurn(CONVERSATION_ID, new DialogueHistoryTurn("turn-1", "밥 먹었어", "잘하셨어요"));
        inOrder.verify(sink).sendCompleted("잘하셨어요");
        inOrder.verify(aiEventPublisher).publishTurnAnalysis(any());
    }

    @Test
    @DisplayName("답변을 앱에 보낸 뒤 그 턴의 음성·발화·답변을 분석 워커로 보낸다 (J1)")
    void handleTurn_publishesAnalysis() {
        // given
        DialogueListener listener = startTurn();

        // when
        listener.onTranscript("밥 먹었어");
        listener.onCompleted("잘하셨어요");

        // then
        TurnAnalysisEvent event = publishedAnalysis();
        assertThat(event.traceId()).isEqualTo("trace-1");
        assertThat(event.turnId()).isEqualTo("turn-1");
        assertThat(event.subjectId()).isEqualTo("subject-1");
        assertThat(event.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(event.audio()).isEqualTo(AUDIO);
        assertThat(event.audioFormat()).isEqualTo("wav");
        assertThat(event.sttText()).isEqualTo("밥 먹었어");
        assertThat(event.llmResponseText()).isEqualTo("잘하셨어요");
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("발화는 인식됐지만 답변 생성에 실패해도 분석은 요청한다 (답변은 비움)")
    void handleTurn_failedReply_stillAnalyzed() {
        // given
        DialogueListener listener = startTurn();

        // when
        listener.onTranscript("밥 먹었어");
        listener.onFailed(new DialogueFailure(DialogueFailureStage.LLM, false, null));

        // then
        TurnAnalysisEvent event = publishedAnalysis();
        assertThat(event.sttText()).isEqualTo("밥 먹었어");
        assertThat(event.llmResponseText()).isNull();
    }

    @Test
    @DisplayName("AI가 응급을 감지하면 기록·보호자 알림을 맡기고 앱에 EMERGENCY를 보낸 뒤, 답변은 평소처럼 이어서 전달한다")
    void emergency() {
        // given
        DialogueListener listener = startTurn();

        // when
        listener.onTranscript("아이고 넘어졌어");
        listener.onEmergency(EmergencyCategory.FALL);
        listener.onCompleted("많이 놀라셨죠");

        // then
        InOrder inOrder = inOrder(emergencyService, sink);
        inOrder.verify(sink).sendTranscript("아이고 넘어졌어");
        inOrder.verify(emergencyService).report(CONVERSATION_ID, "turn-1", EmergencyCategory.FALL);
        inOrder.verify(sink).sendEmergency(EmergencyCategory.FALL);
        inOrder.verify(sink).sendCompleted("많이 놀라셨죠");
    }

    @Test
    @DisplayName("응급 기록이 실패해도 앱에는 EMERGENCY를 보내고 턴은 끊지 않는다 (안내 음성이 먼저)")
    void emergency_reportFails() {
        // given
        DialogueListener listener = startTurn();
        willThrow(new DataAccessResourceFailureException("db down")).given(emergencyService)
                .report(CONVERSATION_ID, "turn-1", EmergencyCategory.PAIN);

        // when
        listener.onTranscript("가슴이 아파");
        listener.onEmergency(EmergencyCategory.PAIN);
        listener.onCompleted("괜찮으세요?");

        // then
        verify(sink).sendEmergency(EmergencyCategory.PAIN);
        verify(sink).sendCompleted("괜찮으세요?");
        verify(sink, never()).sendFailed(any());
    }

    @Test
    @DisplayName("음성 변환만 실패해 답변 텍스트가 있으면 대화 이력에 남기고 실패를 전달한다")
    void handleTurn_failureWithText() {
        // given
        DialogueListener listener = startTurn();
        DialogueFailure failure = new DialogueFailure(DialogueFailureStage.TTS, true, "잘하셨어요");

        // when
        listener.onTranscript("밥 먹었어");
        listener.onFailed(failure);

        // then
        verify(recorder).recordAiReply(CONVERSATION_ID, "turn-1", "잘하셨어요");
        verify(contextService).appendTurn(CONVERSATION_ID, new DialogueHistoryTurn("turn-1", "밥 먹었어", "잘하셨어요"));
        verify(recorder, never()).endTurnWithoutReply(any());
        verify(sink).sendFailed(failure);
    }

    @Test
    @DisplayName("답변 텍스트 없이 실패하면 턴만 끝내고 실패를 전달한다")
    void handleTurn_failureWithoutText() {
        // given
        DialogueListener listener = startTurn();

        // when
        listener.onFailed(DialogueFailure.unavailable());

        // then
        verify(recorder).endTurnWithoutReply(CONVERSATION_ID);
        verify(recorder, never()).recordAiReply(any(), any(), any());
        verify(contextService, never()).appendTurn(any(), any());
        verify(sink).sendFailed(DialogueFailure.unavailable());
        verify(aiEventPublisher, never()).publishTurnAnalysis(any());
    }

    @Test
    @DisplayName("턴을 시작할 수 없으면 AI 서버를 호출하지 않고 예외를 그대로 던진다")
    void handleTurn_beginRejected() {
        // given
        given(recorder.beginTurn(MEMBER_ID, CONVERSATION_ID))
                .willThrow(new CustomException(ErrorCode.CONVERSATION_TURN_IN_PROGRESS));

        // when & then
        assertThatThrownBy(() -> voiceTurnService.handleTurn(MEMBER_ID, CONVERSATION_ID, AUDIO, "wav", sink))
                .isInstanceOf(CustomException.class);
        verify(dialogueClient, never()).converse(any(), any());
        verify(contextService, never()).loadRecentTurns(any());
    }

    @Test
    @DisplayName("선제 첫 안부는 BE 문장을 AI가 읽게 하고, 음성 조각 → 같은 문장의 COMPLETED를 보낸다 (J4)")
    void startProactiveGreeting() {
        // given
        DialogueListener listener = startGreeting();

        // when
        listener.onAudioChunk(new byte[]{7}, 1);
        listener.onCompleted(GREETING);

        // then
        ArgumentCaptor<SpeechRequest> request = ArgumentCaptor.forClass(SpeechRequest.class);
        verify(dialogueClient).speak(request.capture(), any());
        assertThat(request.getValue()).isEqualTo(
                new SpeechRequest("trace-g", "subject-1", CONVERSATION_ID, "turn-g", GREETING));
        verify(contextService).appendTurn(CONVERSATION_ID, new DialogueHistoryTurn("turn-g", "", GREETING));
        InOrder inOrder = inOrder(sink, recorder);
        inOrder.verify(sink).sendAudioChunk(new byte[]{7}, 1);
        inOrder.verify(recorder).endTurnWithoutReply(CONVERSATION_ID);
        inOrder.verify(sink).sendCompleted(GREETING);
        verify(sink, never()).sendTranscript(any());
        verify(aiEventPublisher, never()).publishTurnAnalysis(any());
    }

    @Test
    @DisplayName("AI가 안부를 읽지 못하면(미지원·실패) 음성 없이 문장만 COMPLETED로 보내 앱이 기기 음성으로 읽게 한다")
    void startProactiveGreeting_speechFails() {
        // given
        DialogueListener listener = startGreeting();

        // when
        listener.onFailed(DialogueFailure.unavailable());

        // then
        verify(recorder).endTurnWithoutReply(CONVERSATION_ID);
        verify(sink).sendCompleted(GREETING);
        verify(sink, never()).sendFailed(any());
    }

    @Test
    @DisplayName("첫 안부를 시작할 수 없으면 AI를 부르지 않고 예외를 그대로 던진다")
    void startProactiveGreeting_rejected() {
        // given
        given(recorder.beginGreeting(MEMBER_ID, CONVERSATION_ID))
                .willThrow(new CustomException(ErrorCode.VOICE_PROTOCOL_VIOLATION));

        // when & then
        assertThatThrownBy(() -> voiceTurnService.startProactiveGreeting(MEMBER_ID, CONVERSATION_ID, sink))
                .isInstanceOf(CustomException.class);
        verify(dialogueClient, never()).speak(any(), any());
    }

    @Test
    @DisplayName("빈 오디오는 턴을 시작하지 않고 거절한다")
    void handleTurn_emptyAudio() {
        // when & then
        assertThatThrownBy(() -> voiceTurnService.handleTurn(MEMBER_ID, CONVERSATION_ID, new byte[0], "wav", sink))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.EMPTY_AUDIO));
        verify(recorder, never()).beginTurn(any(), any());
    }

    @Test
    @DisplayName("발화 저장 중 DB 오류가 나면 턴을 끝내고 실패를 전달하며, 이후 이벤트는 무시한다")
    void handleTurn_recordingFailure() {
        // given
        DialogueListener listener = startTurn();
        willThrow(new DataAccessResourceFailureException("db down"))
                .given(recorder).recordElderlyUtterance(CONVERSATION_ID, "turn-1", "밥 먹었어");

        // when
        listener.onTranscript("밥 먹었어");
        listener.onAudioChunk(new byte[]{9}, 0);
        listener.onCompleted("잘하셨어요");

        // then
        verify(sink).sendFailed(DialogueFailure.unavailable());
        verify(sink, never()).sendTranscript(any());
        verify(sink, never()).sendAudioChunk(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(sink, never()).sendCompleted(any());
        verify(recorder).endTurnWithoutReply(CONVERSATION_ID);
    }
}
