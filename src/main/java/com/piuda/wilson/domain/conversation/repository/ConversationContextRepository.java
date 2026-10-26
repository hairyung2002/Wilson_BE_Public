package com.piuda.wilson.domain.conversation.repository;

import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 대화 맥락(최근 턴) 캐시. 대화별 Redis 리스트에 턴을 오래된 순으로 쌓는다.
 *
 * <p>어르신은 대화 사이 침묵이 길어서 고정 TTL이면 대화 도중 맥락이 사라진다.
 * 그래서 읽거나 덧붙일 때마다 TTL을 다시 건다 (Sliding Expiration). 원본은 DB message 테이블이다.</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ConversationContextRepository {

    private static final String KEY_PREFIX = "conversation-context:";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    /** 맥락이 있으면 만료 시간을 늘리고 돌려준다. 없거나 깨졌으면 빈 값. */
    public Optional<List<DialogueHistoryTurn>> findRecentTurns(Long conversationId, Duration ttl) {
        String key = key(conversationId);
        List<String> values = redisTemplate.opsForList().range(key, 0, -1);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        try {
            List<DialogueHistoryTurn> turns = values.stream()
                    .map(value -> jsonMapper.readValue(value, DialogueHistoryTurn.class))
                    .toList();
            redisTemplate.expire(key, ttl);
            return Optional.of(turns);
        } catch (JacksonException e) {
            log.warn("Corrupted conversation context discarded: conversationId={}", conversationId);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    /**
     * 맥락이 있을 때만 턴을 덧붙이고 최근 maxTurns개만 남긴다.
     *
     * <p>만료된 뒤 덧붙이면 새 턴 하나뿐인 불완전한 맥락이 생기므로, 없으면 아무것도 하지 않는다
     * (다음 턴에 DB에서 전체를 다시 채운다).</p>
     */
    public void append(Long conversationId, DialogueHistoryTurn turn, int maxTurns, Duration ttl) {
        String key = key(conversationId);
        Long size = redisTemplate.opsForList().rightPushIfPresent(key, jsonMapper.writeValueAsString(turn));
        if (size == null || size == 0L) {
            return;
        }
        redisTemplate.opsForList().trim(key, -maxTurns, -1);
        redisTemplate.expire(key, ttl);
    }

    public void replaceAll(Long conversationId, List<DialogueHistoryTurn> turns, Duration ttl) {
        String key = key(conversationId);
        redisTemplate.delete(key);
        if (turns.isEmpty()) {
            return;
        }
        redisTemplate.opsForList().rightPushAll(key, turns.stream().map(jsonMapper::writeValueAsString).toList());
        redisTemplate.expire(key, ttl);
    }

    public void delete(Long conversationId) {
        redisTemplate.delete(key(conversationId));
    }

    private String key(Long conversationId) {
        return KEY_PREFIX + conversationId;
    }
}
