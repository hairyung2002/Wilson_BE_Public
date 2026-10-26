package com.piuda.wilson.domain.notification.repository;

import com.piuda.wilson.domain.notification.entity.Notification;
import com.piuda.wilson.domain.notification.entity.PushStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByIdDesc(Long recipientMemberId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientMemberId);

    long countByRecipientIdAndIsReadFalse(Long recipientMemberId);

    List<Notification> findByPushStatusOrderByIdAsc(PushStatus pushStatus);

    @Modifying
    @Query("update Notification n set n.isRead = true where n.recipient.id = :recipientId and n.isRead = false")
    int markAllRead(@Param("recipientId") Long recipientMemberId);
}
