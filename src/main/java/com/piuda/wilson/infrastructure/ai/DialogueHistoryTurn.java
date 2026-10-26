package com.piuda.wilson.infrastructure.ai;

/** 같은 대화의 앞선 턴 하나 (노인 발화 + AI 답변). AI 서버에 대화 맥락으로 함께 보낸다. */
public record DialogueHistoryTurn(String turnId, String sttText, String llmResponseText) {
}
