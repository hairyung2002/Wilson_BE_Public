package com.piuda.wilson.domain.caregiver.entity;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.notification.entity.NotificationType;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "care_relationship",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_care_relationship",
                columnNames = {"caregiver_id", "elderly_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareRelationship extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caregiver_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_care_relationship_caregiver"))
    private Caregiver caregiver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "elderly_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_care_relationship_elderly"))
    private Elderly elderly;

    @Column(name = "relation", length = 30)
    private String relation;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private CareRole role;

    /** 알림 설정 (api-spec 16-1). null이면 역할 기본값: OWNER는 받음, MEMBER는 받지 않음. */
    @Column(name = "notify_pattern_change")
    private Boolean notifyPatternChange;

    @Column(name = "notify_proactive_no_response")
    private Boolean notifyProactiveNoResponse;

    @Column(name = "notify_daily_summary")
    private Boolean notifyDailySummary;

    @Builder
    private CareRelationship(Caregiver caregiver, Elderly elderly, String relation, CareRole role) {
        this.caregiver = caregiver;
        this.elderly = elderly;
        this.relation = relation;
        this.role = role;
    }

    public boolean isOwner() {
        return role == CareRole.OWNER;
    }

    public boolean isOf(Caregiver caregiver) {
        return this.caregiver.getId().equals(caregiver.getId());
    }

    public void promoteToOwner() {
        this.role = CareRole.OWNER;
    }

    public void demoteToMember() {
        this.role = CareRole.MEMBER;
    }

    /** 응급은 끌 수 없다. 나머지는 직접 정한 값, 없으면 역할 기본값을 따른다. */
    public boolean wantsNotification(NotificationType type) {
        return switch (type) {
            case EMERGENCY -> true;
            case PATTERN_CHANGE -> preference(notifyPatternChange);
            case PROACTIVE_NO_RESPONSE -> preference(notifyProactiveNoResponse);
            case DAILY_SUMMARY -> preference(notifyDailySummary);
        };
    }

    /** null인 항목은 바꾸지 않는다. */
    public void updateNotificationSettings(Boolean patternChange, Boolean proactiveNoResponse, Boolean dailySummary) {
        if (patternChange != null) {
            this.notifyPatternChange = patternChange;
        }
        if (proactiveNoResponse != null) {
            this.notifyProactiveNoResponse = proactiveNoResponse;
        }
        if (dailySummary != null) {
            this.notifyDailySummary = dailySummary;
        }
    }

    private boolean preference(Boolean chosen) {
        return chosen != null ? chosen : isOwner();
    }

    public void changeRelation(String relation) {
        this.relation = relation;
    }
}
