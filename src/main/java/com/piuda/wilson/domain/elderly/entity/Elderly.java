package com.piuda.wilson.domain.elderly.entity;

import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.global.common.entity.BaseTimeEntity;
import com.piuda.wilson.global.common.time.QuietHours;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "elderly")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Elderly extends BaseTimeEntity {

    private static final String ERASED_NAME = "삭제된 어르신";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 노인이 직접 가입한 경우에만 존재한다. 보호자가 등록한 노인은 계정이 없다. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", unique = true,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_elderly_member"))
    private Member member;

    @Column(nullable = false)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "care_note", columnDefinition = "text")
    private String careNote;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    /**
     * AI 서버에 노인 PK 대신 보내는 가명 식별자 (AI의 target_user_id). 실명 등 개인정보와 분리해 둔다.
     * 노인을 삭제하면 새 값으로 바꿔, 연구용으로 남긴 지표와의 연결을 끊는다.
     */
    @Column(name = "subject_id", nullable = false, unique = true)
    private UUID subjectId;

    /** 마지막 보호자가 떠나 보호자 없이 남은 시각. 30일 안에 새 보호자가 없으면 파기한다. */
    @Column(name = "orphaned_at")
    private LocalDateTime orphanedAt;

    /** 노인 설정 (api-spec 16-2). 선제 대화를 끄면 일정이 있어도 먼저 말 걸지 않는다. */
    @Column(name = "proactive_enabled", nullable = false)
    private boolean proactiveEnabled = true;

    /** 이 시간에는 선제 대화를 하지 않는다 (KST, 결정 C6). */
    @Column(name = "quiet_hours_start", nullable = false)
    private LocalTime quietHoursStart = QuietHours.DEFAULT.start();

    @Column(name = "quiet_hours_end", nullable = false)
    private LocalTime quietHoursEnd = QuietHours.DEFAULT.end();

    @Builder
    private Elderly(Member member, String name, LocalDate birthDate, Gender gender, String careNote) {
        this.subjectId = UUID.randomUUID();
        this.member = member;
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.careNote = careNote;
    }

    public void updateCareNote(String careNote) {
        this.careNote = careNote;
    }

    /** null인 항목은 변경하지 않는다. */
    public void updateProfile(String name, LocalDate birthDate) {
        if (name != null) {
            this.name = name;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
    }

    public void linkMember(Member member) {
        this.member = member;
    }

    public void delete() {
        this.deleted = true;
    }

    public QuietHours quietHours() {
        return new QuietHours(quietHoursStart, quietHoursEnd);
    }

    /** 방해 금지 구간은 검증된 값으로만 바꾼다. null인 항목은 그대로 둔다. */
    public void updateSettings(Boolean proactiveEnabled, QuietHours quietHours) {
        if (proactiveEnabled != null) {
            this.proactiveEnabled = proactiveEnabled;
        }
        this.quietHoursStart = quietHours.start();
        this.quietHoursEnd = quietHours.end();
    }

    /** 이미 보호자 없는 상태면 처음 시각을 유지한다 (유예 기간이 늘어나지 않도록). */
    public void markOrphaned(LocalDateTime now) {
        if (this.orphanedAt == null) {
            this.orphanedAt = now;
        }
    }

    public void clearOrphaned() {
        this.orphanedAt = null;
    }

    /** 개인정보를 파기하고 가명 식별자를 바꿔 남은 연구용 지표와의 연결을 끊는다 (api-spec 5-5). */
    public void erasePersonalData() {
        this.name = ERASED_NAME;
        this.birthDate = null;
        this.gender = null;
        this.careNote = null;
        this.subjectId = UUID.randomUUID();
        this.orphanedAt = null;
        this.deleted = true;
    }

    /** AI와 안부 문장에서 어르신을 부르는 말. 실명 대신 쓴다 (결정 J7). 성별을 모르면 "어르신". */
    public String addressTerm() {
        if (gender == null) {
            return "어르신";
        }
        return gender == Gender.FEMALE ? "할머니" : "할아버지";
    }

    public Integer ageAt(LocalDate today) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, today).getYears();
    }
}
