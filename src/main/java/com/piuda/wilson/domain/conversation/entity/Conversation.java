package com.piuda.wilson.domain.conversation.entity;

import com.piuda.wilson.domain.elderly.entity.Elderly;
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
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "conversation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "elderly_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_conversation_elderly"))
    private Elderly elderly;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", nullable = false, length = 20)
    private ConversationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationState state;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConversationOrigin origin;

    /** 보호자용 요약. 대화가 끝난 뒤 AI 워커가 만들어 보내며, 그 전에는 null이다 (결정 J2). */
    @Column(length = 200)
    private String summary;

    @Builder
    private Conversation(Elderly elderly, ConversationChannel channel, ConversationType type,
                         ConversationOrigin origin, LocalDateTime startedAt) {
        this.elderly = elderly;
        this.channel = channel;
        this.type = type;
        this.origin = origin == null ? ConversationOrigin.USER : origin;
        this.startedAt = startedAt;
        this.state = ConversationState.WAITING;
    }

    public boolean isProactive() {
        return origin == ConversationOrigin.PROACTIVE;
    }

    public void finish(LocalDateTime endedAt) {
        this.state = ConversationState.FINISHED;
        this.endedAt = endedAt;
    }

    public boolean isTurnInProgress() {
        return state == ConversationState.LISTENING
                || state == ConversationState.PROCESSING
                || state == ConversationState.RESPONDING;
    }

    public void startTurn() {
        this.state = ConversationState.PROCESSING;
    }

    public void startResponding() {
        if (state == ConversationState.PROCESSING) {
            this.state = ConversationState.RESPONDING;
        }
    }

    /** 턴 도중 대화가 종료되었으면 종료 상태를 유지한다. */
    public void endTurn() {
        if (!isFinished()) {
            this.state = ConversationState.WAITING;
        }
    }

    public boolean isFinished() {
        return state == ConversationState.FINISHED || state == ConversationState.FAILED;
    }

    /** 대화한 시간(분). 아직 끝나지 않은 대화는 0으로 본다 (하루 요약·대시보드). */
    public long minutesTalked() {
        return endedAt == null ? 0 : Duration.between(startedAt, endedAt).toMinutes();
    }

    public boolean isOwnedBy(Long elderlyId) {
        return elderly.getId().equals(elderlyId);
    }

    public void delete() {
        this.deleted = true;
    }

    /** 같은 대화에 요약이 다시 오면 덮어쓴다. */
    public void updateSummary(String summary) {
        this.summary = summary;
    }
}
