package com.piuda.wilson.domain.conversation.repository;

import com.piuda.wilson.domain.conversation.entity.ProactiveStatus;
import com.piuda.wilson.domain.conversation.entity.ProactiveTrigger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProactiveTriggerRepository extends JpaRepository<ProactiveTrigger, String> {

    Optional<ProactiveTrigger> findByConversationId(Long conversationId);

    List<ProactiveTrigger> findByStatusAndSentAtLessThanEqual(ProactiveStatus status, LocalDateTime sentAt);

    List<ProactiveTrigger> findByStatusAndRetriedAtLessThanEqual(ProactiveStatus status, LocalDateTime retriedAt);

    long countByElderlyIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
            Long elderlyId, ProactiveStatus status, LocalDateTime from, LocalDateTime to);

    void deleteByElderlyId(Long elderlyId);
}
