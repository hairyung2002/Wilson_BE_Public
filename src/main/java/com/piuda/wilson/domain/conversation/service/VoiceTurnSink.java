package com.piuda.wilson.domain.conversation.service;

import com.piuda.wilson.infrastructure.ai.DialogueFailure;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;

/**
 * 음성 턴 결과를 앱으로 내보내는 출력 포트 (WebSocket 등).
 *
 * <p>구현체는 전송 실패(연결 끊김 등)를 스스로 처리하고 예외를 던지지 않는다.</p>
 */
public interface VoiceTurnSink {

    void sendTranscript(String sttText);

    void sendEmergency(EmergencyCategory category);

    void sendAudioChunk(byte[] data, int sequence);

    void sendCompleted(String fullLlmResponseText);

    void sendFailed(DialogueFailure failure);
}
