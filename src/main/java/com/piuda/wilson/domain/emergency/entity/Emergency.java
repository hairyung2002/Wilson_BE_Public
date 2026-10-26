package com.piuda.wilson.domain.emergency.entity;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.global.common.entity.BaseTimeEntity;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
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

/**
 * AI가 노인 발화에서 감지한 응급 기록 (api-spec 15-2). 대화 원문은 담지 않고 어느 턴인지만 남긴다.
 * notified는 보호자에게 알렸는지이며, 10분 안의 같은 종류 재감지는 기록만 하고 false로 남는다.
 */
@Getter
@Entity
@Table(name = "emergency")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Emergency extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "elderly_id", nullable = false, foreignKey = @ForeignKey(name = "fk_emergency_elderly"))
    private Elderly elderly;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "turn_id", nullable = false, length = 36)
    private String turnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmergencyCategory category;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(nullable = false)
    private boolean notified;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by", foreignKey = @ForeignKey(name = "fk_emergency_acknowledged_by"))
    private Member acknowledgedBy;

    @Builder
    private Emergency(Elderly elderly, Long conversationId, String turnId, EmergencyCategory category,
                      LocalDateTime detectedAt, boolean notified) {
        this.elderly = elderly;
        this.conversationId = conversationId;
        this.turnId = turnId;
        this.category = category;
        this.detectedAt = detectedAt;
        this.notified = notified;
    }

    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }

    /** 보호자 한 명이 확인하면 모두에게 확인됨으로 보인다. 처음 확인한 사람을 남긴다. */
    public void acknowledge(Member member, LocalDateTime now) {
        if (isAcknowledged()) {
            return;
        }
        this.acknowledgedBy = member;
        this.acknowledgedAt = now;
    }
}
