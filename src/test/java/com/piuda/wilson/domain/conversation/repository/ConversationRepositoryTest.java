package com.piuda.wilson.domain.conversation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ConversationRepositoryTest {

    private static final List<ConversationState> CLOSED_STATES =
            List.of(ConversationState.FINISHED, ConversationState.FAILED);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    @DisplayName("노인의 진행 중인 대화만 조회하고 종료·삭제된 대화와 다른 노인의 대화는 제외한다")
    void findActive() {
        // given
        Elderly elderly = persistElderly("김철수");
        Elderly other = persistElderly("이영희");
        Conversation active = persistConversation(elderly, LocalDateTime.now());
        Conversation finished = persistConversation(elderly, LocalDateTime.now());
        finished.finish(LocalDateTime.now());
        Conversation deleted = persistConversation(elderly, LocalDateTime.now());
        deleted.delete();
        persistConversation(other, LocalDateTime.now());
        flushAndClear();

        // when
        List<Conversation> result =
                conversationRepository.findByElderlyIdAndStateNotInAndDeletedFalse(elderly.getId(), CLOSED_STATES);

        // then
        assertThat(result).extracting(Conversation::getId).containsExactly(active.getId());
    }

    @Test
    @DisplayName("노인 삭제 시: 노인의 대화 식별자를 모두 찾고, 한 번에 숨김 처리한다 (다른 노인의 대화는 그대로)")
    void findIdsAndSoftDeleteByElderly() {
        // given
        Elderly elderly = persistElderly("김철수");
        Elderly other = persistElderly("이영희");
        Conversation first = persistConversation(elderly, LocalDateTime.now());
        Conversation second = persistConversation(elderly, LocalDateTime.now());
        Conversation others = persistConversation(other, LocalDateTime.now());
        flushAndClear();

        // when
        List<Long> ids = conversationRepository.findIdsByElderlyId(elderly.getId());
        int updated = conversationRepository.softDeleteAllByElderlyId(elderly.getId());
        flushAndClear();

        // then
        assertThat(ids).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(updated).isEqualTo(2);
        assertThat(conversationRepository.findByIdAndDeletedFalse(first.getId())).isEmpty();
        assertThat(conversationRepository.findByIdAndDeletedFalse(others.getId())).isPresent();
    }

    @Test
    @DisplayName("삭제된 대화는 식별자로 조회되지 않는다")
    void findByIdAndDeletedFalse() {
        // given
        Conversation conversation = persistConversation(persistElderly("김철수"), LocalDateTime.now());
        conversation.delete();
        flushAndClear();

        // when & then
        assertThat(conversationRepository.findByIdAndDeletedFalse(conversation.getId())).isEmpty();
    }

    @Test
    @DisplayName("노인의 대화 목록을 최신순 페이지로 조회하고 삭제된 대화는 제외한다")
    void findPage() {
        // given
        Elderly elderly = persistElderly("김철수");
        Conversation older = persistConversation(elderly, LocalDateTime.of(2026, 10, 14, 9, 0));
        Conversation newer = persistConversation(elderly, LocalDateTime.of(2026, 10, 15, 9, 0));
        persistConversation(elderly, LocalDateTime.of(2026, 10, 16, 9, 0)).delete();
        flushAndClear();

        // when
        Page<Conversation> page = conversationRepository
                .findByElderlyIdAndDeletedFalseOrderByStartedAtDescIdDesc(elderly.getId(), PageRequest.of(0, 10));

        // then
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Conversation::getId).containsExactly(newer.getId(), older.getId());
    }

    @Test
    @DisplayName("지정한 날짜에 시작한 대화만 조회한다")
    void findPageOnDate() {
        // given
        Elderly elderly = persistElderly("김철수");
        persistConversation(elderly, LocalDateTime.of(2026, 10, 14, 23, 59));
        Conversation target = persistConversation(elderly, LocalDateTime.of(2026, 10, 15, 0, 0));
        persistConversation(elderly, LocalDateTime.of(2026, 10, 16, 0, 0));
        flushAndClear();

        // when
        Page<Conversation> page = conversationRepository.findPageByElderlyIdAndStartedAtRange(
                elderly.getId(),
                LocalDateTime.of(2026, 10, 15, 0, 0),
                LocalDateTime.of(2026, 10, 16, 0, 0),
                PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).extracting(Conversation::getId).containsExactly(target.getId());
    }

    private Elderly persistElderly(String name) {
        return entityManager.persist(Elderly.builder().name(name).build());
    }

    private Conversation persistConversation(Elderly elderly, LocalDateTime startedAt) {
        return entityManager.persist(Conversation.builder()
                .elderly(elderly)
                .channel(ConversationChannel.VOICE)
                .type(ConversationType.DAILY)
                .startedAt(startedAt)
                .build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
