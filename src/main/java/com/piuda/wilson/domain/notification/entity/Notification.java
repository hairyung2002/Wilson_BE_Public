package com.piuda.wilson.domain.notification.entity;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 보호자 알림 한 건. 앱 내 알림 목록이며, 푸시 발송 상태를 함께 기록한다 (api-spec 14장). */
@Getter
@Entity
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_member_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_notification_recipient"))
    private Member recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "elderly_id", foreignKey = @ForeignKey(name = "fk_notification_elderly"))
    private Elderly elderly;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_status", nullable = false, length = 15)
    private PushStatus pushStatus;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Builder
    private Notification(Member recipient, Elderly elderly, NotificationType type, String title, String body,
                         PushStatus pushStatus) {
        this.recipient = recipient;
        this.elderly = elderly;
        this.type = type;
        this.title = title;
        this.body = body;
        this.pushStatus = pushStatus == null ? PushStatus.QUEUED : pushStatus;
        this.isRead = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }

    public void recordPush(PushStatus status, LocalDateTime now) {
        this.pushStatus = status;
        if (status == PushStatus.SENT) {
            this.sentAt = now;
        }
    }
}
