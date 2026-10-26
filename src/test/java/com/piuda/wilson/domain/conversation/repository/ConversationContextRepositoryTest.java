package com.piuda.wilson.domain.conversation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ConversationContextRepositoryTest {

    private static final String KEY = "conversation-context:7";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DialogueHistoryTurn TURN = new DialogueHistoryTurn("turn-1", "밥 먹었어", "잘하셨어요");
    private static final String TURN_JSON = JSON.writeValueAsString(TURN);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private ConversationContextRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ConversationContextRepository(redisTemplate, JSON);
    }

    @Test
    @DisplayName("맥락이 깨져 읽을 수 없으면 지우고 빈 값을 돌려준다 (DB에서 다시 채우도록)")
    void findRecentTurns_corrupted() {
        // given
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.range(KEY, 0, -1)).willReturn(List.of("not-json"));

        // when & then
        assertThat(repository.findRecentTurns(7L, TTL)).isEmpty();
        verify(redisTemplate).delete(KEY);
    }

    @Test
    @DisplayName("저장된 맥락이 있으면 오래된 순으로 돌려주고, 만료 시간을 다시 늘린다 (Sliding Expiration)")
    void findRecentTurns_hit() {
        // given
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.range(KEY, 0, -1)).willReturn(List.of(TURN_JSON));

        // when
        var turns = repository.findRecentTurns(7L, TTL);

        // then
        assertThat(turns).contains(List.of(TURN));
        verify(redisTemplate).expire(KEY, TTL);
    }

    @Test
    @DisplayName("저장된 맥락이 없으면 빈 값을 돌려주고 만료 시간을 건드리지 않는다")
    void findRecentTurns_miss() {
        // given
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.range(KEY, 0, -1)).willReturn(List.of());

        // when & then
        assertThat(repository.findRecentTurns(7L, TTL)).isEmpty();
        verify(redisTemplate, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    @DisplayName("맥락이 있을 때만 새 턴을 덧붙이고, 최근 N턴만 남긴 뒤 만료 시간을 늘린다")
    void append_whenPresent() {
        // given
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.rightPushIfPresent(KEY, TURN_JSON)).willReturn(3L);

        // when
        repository.append(7L, TURN, 10, TTL);

        // then
        verify(listOperations).trim(KEY, -10, -1);
        verify(redisTemplate).expire(KEY, TTL);
    }

    @Test
    @DisplayName("맥락이 만료됐으면 새 턴만 담긴 불완전한 맥락을 만들지 않는다 (다음 턴에 DB에서 다시 채움)")
    void append_whenAbsent() {
        // given
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.rightPushIfPresent(KEY, TURN_JSON)).willReturn(0L);

        // when
        repository.append(7L, TURN, 10, TTL);

        // then
        verify(listOperations, never()).trim(anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
        verify(redisTemplate, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    @DisplayName("DB에서 다시 읽은 맥락으로 통째로 교체하고 만료 시간을 건다")
    void replaceAll() {
        // given
        given(redisTemplate.opsForList()).willReturn(listOperations);

        // when
        repository.replaceAll(7L, List.of(TURN), TTL);

        // then
        verify(redisTemplate).delete(KEY);
        verify(listOperations).rightPushAll(KEY, List.of(TURN_JSON));
        verify(redisTemplate).expire(KEY, TTL);
    }

    @Test
    @DisplayName("교체할 턴이 없으면 키만 지운다")
    void replaceAll_empty() {
        // when
        repository.replaceAll(7L, List.of(), TTL);

        // then
        verify(redisTemplate).delete(KEY);
        verify(redisTemplate, never()).opsForList();
    }

    @Test
    @DisplayName("대화 맥락을 삭제한다")
    void delete() {
        // when
        repository.delete(7L);

        // then
        verify(redisTemplate).delete(KEY);
    }
}
