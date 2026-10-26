package com.piuda.wilson.domain.conversation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class MessageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MessageRepository messageRepository;

    @Test
    @DisplayName("대화에 속한 메시지를 시간순으로 조회한다")
    void findByConversation() {
        // given
        Member member = entityManager.persist(Member.builder()
                .email("elder@wilson.com").password("p").name("노인").memberType(MemberType.ELDERLY).build());
        Elderly elderly = entityManager.persist(Elderly.builder().member(member).name("노인").build());
        Conversation conversation = entityManager.persist(Conversation.builder()
                .elderly(elderly).channel(ConversationChannel.VOICE).type(ConversationType.DAILY)
                .startedAt(LocalDateTime.now()).build());
        entityManager.persist(Message.builder()
                .conversation(conversation).sender(MessageSender.AI).content("식사는 하셨어요?").build());
        entityManager.persist(Message.builder()
                .conversation(conversation).sender(MessageSender.ELDERLY).content("응 먹었어").build());
        entityManager.flush();

        // when
        List<Message> messages =
                messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId());

        // then
        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(Message::getContent)
                .containsExactly("식사는 하셨어요?", "응 먹었어");
    }

    @Test
    @DisplayName("지정한 대화들의 메시지 원문을 한 번에 지운다 (노인 삭제 시 원문 파기)")
    void deleteByConversationIdIn() {
        // given
        Elderly elderly = entityManager.persist(Elderly.builder().name("노인").build());
        Conversation target = entityManager.persist(Conversation.builder()
                .elderly(elderly).channel(ConversationChannel.VOICE).type(ConversationType.DAILY)
                .startedAt(LocalDateTime.now()).build());
        Conversation kept = entityManager.persist(Conversation.builder()
                .elderly(elderly).channel(ConversationChannel.VOICE).type(ConversationType.DAILY)
                .startedAt(LocalDateTime.now()).build());
        entityManager.persist(Message.builder().conversation(target).sender(MessageSender.ELDERLY).content("원문").build());
        entityManager.persist(Message.builder().conversation(kept).sender(MessageSender.ELDERLY).content("남김").build());
        entityManager.flush();

        // when
        int deleted = messageRepository.deleteByConversationIdIn(List.of(target.getId()));
        entityManager.clear();

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(target.getId())).isEmpty();
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(kept.getId())).hasSize(1);
    }

    @Test
    @DisplayName("대화의 최근 메시지를 최신순으로 지정한 개수만큼 조회한다")
    void findRecentByConversation() {
        // given
        Elderly elderly = entityManager.persist(Elderly.builder().name("노인").build());
        Conversation conversation = entityManager.persist(Conversation.builder()
                .elderly(elderly).channel(ConversationChannel.VOICE).type(ConversationType.DAILY)
                .startedAt(LocalDateTime.now()).build());
        for (String content : List.of("첫째", "둘째", "셋째")) {
            entityManager.persist(Message.builder()
                    .conversation(conversation).sender(MessageSender.ELDERLY).content(content).build());
        }
        entityManager.flush();

        // when
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(
                conversation.getId(), PageRequest.of(0, 2));

        // then
        assertThat(messages).extracting(Message::getContent).containsExactly("셋째", "둘째");
    }
}
