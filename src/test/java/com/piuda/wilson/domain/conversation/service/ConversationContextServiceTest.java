package com.piuda.wilson.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import com.piuda.wilson.domain.conversation.repository.ConversationContextRepository;
import com.piuda.wilson.domain.conversation.repository.MessageRepository;
import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class ConversationContextServiceTest {

    private static final Long CONVERSATION_ID = 7L;
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_TURNS = 2;
    private static final DialogueHistoryTurn TURN = new DialogueHistoryTurn("turn-1", "밥 먹었어", "잘하셨어요");

    @Mock
    private ConversationContextRepository contextRepository;

    @Mock
    private MessageRepository messageRepository;

    private ConversationContextService contextService;

    @BeforeEach
    void setUp() {
        contextService = new ConversationContextService(contextRepository, messageRepository,
                new ConversationContextProperties(TTL, MAX_TURNS));
    }

    private static Message message(String turnId, MessageSender sender, String content) {
        return Message.builder().turnId(turnId).sender(sender).content(content).build();
    }

    @Test
    @DisplayName("Redis에 맥락이 있으면 그대로 쓰고 DB는 읽지 않는다")
    void loadRecentTurns_cacheHit() {
        // given
        given(contextRepository.findRecentTurns(CONVERSATION_ID, TTL)).willReturn(Optional.of(List.of(TURN)));

        // when
        List<DialogueHistoryTurn> turns = contextService.loadRecentTurns(CONVERSATION_ID);

        // then
        assertThat(turns).containsExactly(TURN);
        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    @DisplayName("맥락이 만료됐으면 DB 대화 이력에서 최근 N턴을 오래된 순으로 다시 만들어 Redis에 채운다")
    void loadRecentTurns_cacheMiss_rebuildsFromDb() {
        // given
        given(contextRepository.findRecentTurns(CONVERSATION_ID, TTL)).willReturn(Optional.empty());
        given(messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(
                CONVERSATION_ID, PageRequest.of(0, MAX_TURNS * 2))).willReturn(List.of(
                message("turn-3", MessageSender.AI, "산책 좋죠"),
                message("turn-3", MessageSender.ELDERLY, "산책 갈래"),
                message("turn-2", MessageSender.ELDERLY, "답 못 받은 말"),
                message("turn-1", MessageSender.AI, "잘하셨어요")));

        // when
        List<DialogueHistoryTurn> turns = contextService.loadRecentTurns(CONVERSATION_ID);

        // then: 답변이 없는 턴, 발화가 잘려 나간 턴은 제외
        List<DialogueHistoryTurn> expected = List.of(new DialogueHistoryTurn("turn-3", "산책 갈래", "산책 좋죠"));
        assertThat(turns).isEqualTo(expected);
        verify(contextRepository).replaceAll(CONVERSATION_ID, expected, TTL);
    }

    @Test
    @DisplayName("대화 전체를 읽었으면 선제 첫 안부(AI 발화만 있는 턴)도 발화 없이 맥락에 넣는다 (J4)")
    void loadRecentTurns_rebuildsWithGreeting() {
        // given
        given(contextRepository.findRecentTurns(CONVERSATION_ID, TTL)).willReturn(Optional.empty());
        given(messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(
                CONVERSATION_ID, PageRequest.of(0, MAX_TURNS * 2))).willReturn(List.of(
                message("turn-1", MessageSender.ELDERLY, "된장찌개 먹었어"),
                message("turn-g", MessageSender.AI, "할머니, 오늘 점심은 뭐 드셨어요?")));

        // when
        List<DialogueHistoryTurn> turns = contextService.loadRecentTurns(CONVERSATION_ID);

        // then: 아직 답변이 없는 turn-1은 제외
        assertThat(turns).containsExactly(new DialogueHistoryTurn("turn-g", "", "할머니, 오늘 점심은 뭐 드셨어요?"));
    }

    @Test
    @DisplayName("Redis 장애면 DB 이력으로 맥락을 만들어 대화를 이어간다")
    void loadRecentTurns_redisDown_fallsBackToDb() {
        // given
        given(contextRepository.findRecentTurns(CONVERSATION_ID, TTL))
                .willThrow(new RedisConnectionFailureException("redis down"));
        given(messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(
                CONVERSATION_ID, PageRequest.of(0, MAX_TURNS * 2))).willReturn(List.of(
                message("turn-1", MessageSender.AI, "잘하셨어요"),
                message("turn-1", MessageSender.ELDERLY, "밥 먹었어")));
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(contextRepository).replaceAll(any(), any(), any());

        // when & then
        assertThat(contextService.loadRecentTurns(CONVERSATION_ID)).containsExactly(TURN);
    }

    @Test
    @DisplayName("Redis·DB 모두 실패하면 맥락 없이 대화를 이어간다")
    void loadRecentTurns_allDown_returnsEmpty() {
        // given
        given(contextRepository.findRecentTurns(CONVERSATION_ID, TTL))
                .willThrow(new RedisConnectionFailureException("redis down"));
        given(messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(any(), any()))
                .willThrow(new DataAccessResourceFailureException("db down"));

        // when & then
        assertThat(contextService.loadRecentTurns(CONVERSATION_ID)).isEmpty();
    }

    @Test
    @DisplayName("끝난 턴을 맥락에 덧붙인다")
    void appendTurn() {
        // when
        contextService.appendTurn(CONVERSATION_ID, TURN);

        // then
        verify(contextRepository).append(CONVERSATION_ID, TURN, MAX_TURNS, TTL);
    }

    @Test
    @DisplayName("맥락 덧붙이기가 Redis 장애로 실패해도 턴 처리를 막지 않는다 (다음 턴에 DB에서 다시 채움)")
    void appendTurn_redisDown() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(contextRepository).append(CONVERSATION_ID, TURN, MAX_TURNS, TTL);

        // when & then (예외 없음)
        contextService.appendTurn(CONVERSATION_ID, TURN);
    }

    @Test
    @DisplayName("대화가 끝나면 맥락을 지우고, Redis 장애여도 종료를 막지 않는다")
    void clear() {
        // given
        willThrow(new RedisConnectionFailureException("redis down")).given(contextRepository).delete(CONVERSATION_ID);

        // when
        contextService.clear(CONVERSATION_ID);

        // then
        verify(contextRepository).delete(CONVERSATION_ID);
    }
}
