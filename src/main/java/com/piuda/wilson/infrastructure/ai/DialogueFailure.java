package com.piuda.wilson.infrastructure.ai;

/**
 * AI 대화 실패 정보.
 *
 * @param recoverable          이미 전달된 음성 조각이 유효한지 (앱 폴백 판단용)
 * @param fullLlmResponseText  답변 텍스트가 완성됐다면 그 텍스트, 없으면 null
 */
public record DialogueFailure(DialogueFailureStage stage, boolean recoverable, String fullLlmResponseText) {

    public static DialogueFailure unavailable() {
        return new DialogueFailure(DialogueFailureStage.UNAVAILABLE, false, null);
    }
}
