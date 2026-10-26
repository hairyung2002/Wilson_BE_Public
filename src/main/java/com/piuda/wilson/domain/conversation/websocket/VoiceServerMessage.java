package com.piuda.wilson.domain.conversation.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.infrastructure.ai.DialogueFailure;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;

/** BE → APP 텍스트 프레임 (docs/websocket-voice-protocol.md 3장). */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "category", "stage", "recoverable", "code", "text", "message"})
public record VoiceServerMessage(String type, String text, String stage, Boolean recoverable,
                                 String code, String message, String category) {

    public static VoiceServerMessage transcript(String sttText) {
        return new VoiceServerMessage("TRANSCRIPT", sttText, null, null, null, null, null);
    }

    /** 앱은 내장 안내 음성을 먼저 재생한 뒤 AI 답변을 이어서 재생한다 (api-spec 15-2). */
    public static VoiceServerMessage emergency(EmergencyCategory category) {
        return new VoiceServerMessage("EMERGENCY", null, null, null, null, null, category.name());
    }

    public static VoiceServerMessage completed(String fullLlmResponseText) {
        return new VoiceServerMessage("COMPLETED", fullLlmResponseText, null, null, null, null, null);
    }

    public static VoiceServerMessage failed(DialogueFailure failure) {
        return new VoiceServerMessage("FAILED", failure.fullLlmResponseText(), failure.stage().name(),
                failure.recoverable(), null, null, null);
    }

    public static VoiceServerMessage error(ErrorCode errorCode) {
        return new VoiceServerMessage("ERROR", null, null, null, errorCode.getCode(), errorCode.getMessage(), null);
    }
}
