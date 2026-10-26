package com.piuda.wilson.domain.conversation.websocket;

/** APP → BE 텍스트 프레임 (docs/websocket-voice-protocol.md 2장, 5-1). */
public record VoiceClientMessage(String type, String audioFormat) {

    static final String TURN_START = "TURN_START";
    static final String TURN_END = "TURN_END";
    static final String PROACTIVE_START = "PROACTIVE_START";
}
