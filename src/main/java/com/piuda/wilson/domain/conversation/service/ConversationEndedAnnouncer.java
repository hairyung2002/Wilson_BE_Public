package com.piuda.wilson.domain.conversation.service;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import com.piuda.wilson.domain.conversation.repository.MessageRepository;
import com.piuda.wilson.infrastructure.ai.ConversationEndedEvent;
import com.piuda.wilson.infrastructure.ai.DialogueHistoryTurn;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 대화가 끝났음을 AI 워커에 알린다 (결정 J2). AI 워커가 RAG에 저장하고 보호자용 요약을 만든다.
 *
 * <p>대화를 끝낸 트랜잭션 안에서 부르고, 실제 발행은 커밋된 뒤에 한다 ({@link ConversationEndedRelay}).
 * 롤백된 종료가 AI로 나가지 않게 하기 위함이다.</p>
 */
@Component
@RequiredArgsConstructor
public class ConversationEndedAnnouncer {

    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 한 마디도 오가지 않은 대화는 알리지 않는다. */
    public void announce(Conversation conversation) {
        List<DialogueHistoryTurn> turns = toTurns(
                messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId()));
        if (turns.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new ConversationEndedEvent(UUID.randomUUID().toString(),
                conversation.getElderly().getSubjectId().toString(), conversation.getId(),
                toInstant(conversation.getStartedAt()), toInstant(conversation.getEndedAt()), turns));
    }

    /** 오래된 순 메시지를 턴으로 묶는다. 선제 안부 턴은 발화가, 답을 받지 못한 턴은 답변이 빈 값이다. */
    private static List<DialogueHistoryTurn> toTurns(List<Message> oldestFirst) {
        Map<String, String[]> textsByTurn = new LinkedHashMap<>();
        for (Message message : oldestFirst) {
            if (Objects.isNull(message.getTurnId())) {
                continue;
            }
            String[] texts = textsByTurn.computeIfAbsent(message.getTurnId(), id -> new String[]{"", ""});
            texts[message.getSender() == MessageSender.ELDERLY ? 0 : 1] = message.getContent();
        }
        return textsByTurn.entrySet().stream()
                .map(entry -> new DialogueHistoryTurn(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
    }

    /** 대화 시각은 서버 시간대(KST) 기준으로 저장된다. */
    private static Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
