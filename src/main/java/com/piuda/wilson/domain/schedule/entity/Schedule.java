package com.piuda.wilson.domain.schedule.entity;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 노인별 반복 일정 (api-spec 11장). 이 시각에 선제 대화(6-2)를 건다. 시각은 KST. */
@Getter
@Entity
@Table(name = "schedule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "elderly_id", nullable = false, foreignKey = @ForeignKey(name = "fk_schedule_elderly"))
    private Elderly elderly;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20)
    private ScheduleType type;

    @Column(length = 30)
    private String label;

    @Column(name = "schedule_time", nullable = false)
    private LocalTime time;

    @Convert(converter = DaysOfWeekConverter.class)
    @Column(name = "days_of_week", nullable = false, length = 30)
    private Set<DayOfWeek> daysOfWeek;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false, length = 10)
    private ScheduleAuthor createdBy;

    @Builder
    private Schedule(Elderly elderly, ScheduleType type, String label, LocalTime time, Set<DayOfWeek> daysOfWeek,
                     boolean enabled, ScheduleAuthor createdBy) {
        this.elderly = elderly;
        this.type = type;
        this.label = label;
        this.time = time;
        this.daysOfWeek = EnumSet.copyOf(daysOfWeek);
        this.enabled = enabled;
        this.createdBy = createdBy;
    }

    public Set<DayOfWeek> getDaysOfWeek() {
        return Collections.unmodifiableSet(daysOfWeek);
    }

    /** null인 항목은 바꾸지 않는다. */
    public void update(ScheduleType type, String label, LocalTime time, Set<DayOfWeek> daysOfWeek, Boolean enabled) {
        if (type != null) {
            this.type = type;
        }
        if (label != null) {
            this.label = label;
        }
        if (time != null) {
            this.time = time;
        }
        if (daysOfWeek != null) {
            this.daysOfWeek = EnumSet.copyOf(daysOfWeek);
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
    }

    /** 같은 시각에 요일이 하나라도 겹치면 같은 시각에 선제 대화가 두 번 걸린다. */
    public boolean overlaps(LocalTime time, Set<DayOfWeek> daysOfWeek) {
        return this.time.equals(time) && daysOfWeek.stream().anyMatch(this.daysOfWeek::contains);
    }

    public boolean runsOn(DayOfWeek day) {
        return daysOfWeek.contains(day);
    }
}
