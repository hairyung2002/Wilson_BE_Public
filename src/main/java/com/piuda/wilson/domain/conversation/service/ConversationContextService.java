package com.piuda.wilson.domain.conversation.service;

import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import com.piuda.wilson.domain.conversation.repository.ConversationContextRepository;
import com.piuda.wilson.domain.conversation.repository.MessageRepository;
import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * AI 서버에 보낼 대화 맥락(최근 턴)을 관리한다. AI 서버는 무상태라 맥락은 BE가 들고 있다.
 *
 * <p>Redis가 1차 저장소이고 원본은 DB 대화 이력이다. Redis가 만료됐거나 장애면 DB에서 다시 만든다.
 * 맥락은 대화 품질을 높이는 부가 정보라, 어떤 장애도 음성 턴 자체를 막지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationContextService {

    private final ConversationContextRepository contextRepository;
    private final MessageRepository messageRepository;
    private final ConversationContextProperties properties;

    /** 같은 대화의 최근 턴을 오래된 순으로 돌려준다. 읽을 때마다 Redis 만료 시간이 늘어난다. */
    public List<DialogueHistoryTurn> loadRecentTurns(Long conversationId) {
        try {
            var cached = contextRepository.findRecentTurns(conversationId, properties.ttl());
            if (cached.isPresent()) {
                return cached.get();
            }
        } catch (DataAccessException e) {
            log.warn("Conversation context cache unavailable, rebuilding from DB: conversationId={}",
                    conversationId, e);
        }
        return rebuildFromHistory(conversationId);
    }

    public void appendTurn(Long conversationId, DialogueHistoryTurn turn) {
        try {
            contextRepository.append(conversationId, turn, properties.maxTurns(), properties.ttl());
        } catch (DataAccessException e) {
            log.warn("Failed to append conversation context: conversationId={}", conversationId, e);
        }
    }

    public void clear(Long conversationId) {
        try {
            contextRepository.delete(conversationId);
        } catch (DataAccessException e) {
            log.warn("Failed to clear conversation context: conversationId={}", conversationId, e);
        }
    }

    private List<DialogueHistoryTurn> rebuildFromHistory(Long conversationId) {
        List<DialogueHistoryTurn> turns;
        try {
            int limit = properties.maxTurns() * 2;
            List<Message> newestFirst = messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(
                    conversationId, PageRequest.of(0, limit));
            turns = toTurns(newestFirst, newestFirst.size() < limit);
        } catch (DataAccessException e) {
            log.error("Conversation history unavailable, continuing without context: conversationId={}",
                    conversationId, e);
            return List.of();
        }
        try {
            contextRepository.replaceAll(conversationId, turns, properties.ttl());
        } catch (DataAccessException e) {
            log.warn("Failed to cache conversation context: conversationId={}", conversationId, e);
        }
        return turns;
    }

    /**
     * 최신순 메시지를 턴 단위로 묶어 오래된 순으로 바꾼다. 답변이 있는 턴만 맥락이 된다.
     * AI 발화만 있는 턴은 선제 첫 안부다(결정 J4). 다만 대화 전체를 읽지 못했으면 가장 오래된 턴의 발화가
     * 잘렸을 수 있어 구분할 수 없으므로 뺀다.
     */
    private static List<DialogueHistoryTurn> toTurns(List<Message> newestFirst, boolean wholeConversation) {
        Map<String, String[]> textsByTurn = new LinkedHashMap<>();
        for (Message message : newestFirst.reversed()) {
            if (message.getTurnId() == null) {
                continue;
            }
            String[] texts = textsByTurn.computeIfAbsent(message.getTurnId(), id -> new String[2]);
            texts[message.getSender() == MessageSender.ELDERLY ? 0 : 1] = message.getContent();
        }
        List<DialogueHistoryTurn> turns = new ArrayList<>();
        textsByTurn.forEach((turnId, texts) -> {
            if (Objects.isNull(texts[1])) {
                return;
            }
            if (Objects.nonNull(texts[0])) {
                turns.add(new DialogueHistoryTurn(turnId, texts[0], texts[1]));
            } else if (wholeConversation) {
                turns.add(new DialogueHistoryTurn(turnId, "", texts[1]));
            }
        });
        return turns;
    }
}
