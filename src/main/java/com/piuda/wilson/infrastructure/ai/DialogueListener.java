package com.piuda.wilson.infrastructure.ai;

/** AI 대화 응답 스트림 이벤트. 한 호출 안에서 순서대로, 겹치지 않게 호출된다. */
public interface DialogueListener {

    /** 노인 발화 STT 원문 (스트림 첫 프레임). */
    void onTranscript(String sttText);

    /** 발화에서 응급 신호를 감지함 (발화 원문 직후, 감지됐을 때만). 답변 스트림은 평소처럼 이어진다. */
    void onEmergency(EmergencyCategory category);

    /** TTS 음성 조각. sequence 순서대로 전달된다. */
    void onAudioChunk(byte[] data, int sequence);

    /** 정상 종료. 완성된 AI 답변 전체 텍스트. */
    void onCompleted(String fullLlmResponseText);

    /** 실패 종료. */
    void onFailed(DialogueFailure failure);
}
