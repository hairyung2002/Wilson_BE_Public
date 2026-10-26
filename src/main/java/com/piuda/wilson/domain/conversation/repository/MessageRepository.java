package com.piuda.wilson.domain.conversation.repository;

import com.piuda.wilson.domain.conversation.entity.Message;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    /** 선제 대화 첫 안부를 이미 했거나 말이 오간 대화인지 확인한다. */
    boolean existsByConversationId(Long conversationId);

    /** 대화 맥락 재구성용. 최신 메시지부터 pageable 크기만큼만 읽는다. */
    List<Message> findByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId, Pageable pageable);

    /** 노인 삭제 시 대화 원문 파기용 일괄 삭제. */
    @Modifying
    @Query("delete from Message m where m.conversation.id in :conversationIds")
    int deleteByConversationIdIn(@Param("conversationIds") Collection<Long> conversationIds);
}
