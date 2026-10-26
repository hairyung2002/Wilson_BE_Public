package com.piuda.wilson.infrastructure.ai;

/**
 * AI 서버 대화 응답 포트. 노인 발화 오디오 1건을 보내고, 결과를 스트림 이벤트로 받는다.
 *
 * <p>구현체는 어떤 경우에도 예외를 던지지 않고, 반드시 {@link DialogueListener#onCompleted} 또는
 * {@link DialogueListener#onFailed} 중 하나를 정확히 한 번 호출한다.</p>
 */
public interface DialogueClient {

    void converse(DialogueRequest request, DialogueListener listener);

    /** 정해진 문장을 음성으로 받는다. 노인 발화가 없으므로 onTranscript·onEmergency는 호출되지 않는다. */
    void speak(SpeechRequest request, DialogueListener listener);
}
