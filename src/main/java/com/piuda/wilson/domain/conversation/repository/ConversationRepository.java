package com.piuda.wilson.domain.conversation.repository;

import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByIdAndDeletedFalse(Long id);

    List<Conversation> findByElderlyIdAndDeletedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long elderlyId, LocalDateTime from, LocalDateTime to);

    @Query("select c.id from Conversation c where c.elderly.id = :elderlyId")
    List<Long> findIdsByElderlyId(@Param("elderlyId") Long elderlyId);

    @Modifying
    @Query("update Conversation c set c.deleted = true where c.elderly.id = :elderlyId")
    int softDeleteAllByElderlyId(@Param("elderlyId") Long elderlyId);

    List<Conversation> findByElderlyIdAndStateNotInAndDeletedFalse(Long elderlyId, Collection<ConversationState> states);

    Page<Conversation> findByElderlyIdAndDeletedFalseOrderByStartedAtDescIdDesc(Long elderlyId, Pageable pageable);

    @Query(value = """
            select c from Conversation c
            where c.elderly.id = :elderlyId and c.deleted = false
              and c.startedAt >= :from and c.startedAt < :to
            order by c.startedAt desc, c.id desc
            """,
            countQuery = """
            select count(c) from Conversation c
            where c.elderly.id = :elderlyId and c.deleted = false
              and c.startedAt >= :from and c.startedAt < :to
            """)
    Page<Conversation> findPageByElderlyIdAndStartedAtRange(@Param("elderlyId") Long elderlyId,
                                                            @Param("from") LocalDateTime from,
                                                            @Param("to") LocalDateTime to,
                                                            Pageable pageable);
}
