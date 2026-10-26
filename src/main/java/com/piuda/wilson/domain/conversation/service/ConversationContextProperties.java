package com.piuda.wilson.domain.conversation.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대화 맥락 보관 설정.
 *
 * @param ttl      마지막 입력 이후 맥락을 Redis에 남겨 두는 시간. 입력마다 다시 늘어난다
 * @param maxTurns AI 서버에 함께 보낼 최근 턴 수
 */
@ConfigurationProperties("wilson.conversation.context")
public record ConversationContextProperties(Duration ttl, int maxTurns) {
}
