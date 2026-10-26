package com.piuda.wilson.domain.conversation.entity;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.schedule.entity.Schedule;
import com.piuda.wilson.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일정에 따라 노인 기기로 보낸 선제 대화 한 건 (api-spec 6-2). id(UUID)는 푸시에 실려 앱이 대화를 만들 때 쓴다.
 * 일정이 나중에 지워져도 기록은 남도록 일정 ID만 둔다.
 */
@Getter
@Entity
@Table(name = "proactive_trigger")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProactiveTrigger extends BaseTimeEntity {

    /** 푸시 후 이 시간 안에만 대화를 만들 수 있다. */
    private static final Duration USABLE_FOR = Duration.ofMinutes(30);

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "elderly_id", nullable = false, foreignKey = @ForeignKey(name = "fk_proactive_trigger_elderly"))
    private Elderly elderly;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", nullable = false, length = 20)
    private ConversationType conversationType;

    @Column(length = 30)
    private String label;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ProactiveStatus status;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "retried_at")
    private LocalDateTime retriedAt;

    @Column(name = "conversation_id")
    private Long conversationId;

    /** 첫 안부 문장 (결정 J4). 푸시 알림·재푸시·대화 첫 발화에 같은 문장을 쓴다. */
    @Column(nullable = false, length = 100)
    private String greeting;

    private ProactiveTrigger(Schedule schedule, LocalDateTime scheduledAt, String greeting) {
        this.id = UUID.randomUUID().toString();
        this.elderly = schedule.getElderly();
        this.scheduleId = schedule.getId();
        this.conversationType = schedule.getType().toConversationType();
        this.label = schedule.getLabel();
        this.scheduledAt = scheduledAt;
        this.sentAt = scheduledAt;
        this.status = ProactiveStatus.SENT;
        this.greeting = greeting;
    }

    public static ProactiveTrigger create(Schedule schedule, LocalDateTime scheduledAt, String greeting) {
        return new ProactiveTrigger(schedule, scheduledAt, greeting);
    }

    public boolean isFor(Long memberId) {
        return elderly.getMember() != null && elderly.getMember().getId().equals(memberId);
    }

    public boolean isUsable(LocalDateTime now) {
        boolean waiting = status == ProactiveStatus.SENT || status == ProactiveStatus.RETRIED;
        return waiting && now.isBefore(sentAt.plus(USABLE_FOR));
    }

    public boolean hasConversation() {
        return conversationId != null;
    }

    public void attachConversation(Long conversationId) {
        this.conversationId = conversationId;
    }

    public void markUndeliverable() {
        this.status = ProactiveStatus.UNDELIVERABLE;
    }

    public void markRetried(LocalDateTime now) {
        this.status = ProactiveStatus.RETRIED;
        this.retriedAt = now;
    }

    /** 이미 끝난(무응답 처리 등) 선제 대화에서 뒤늦게 말해도 상태는 바꾸지 않는다. */
    public void markResponded() {
        if (status == ProactiveStatus.SENT || status == ProactiveStatus.RETRIED) {
            this.status = ProactiveStatus.RESPONDED;
        }
    }

    public void markMissed() {
        this.status = ProactiveStatus.MISSED;
    }
}
