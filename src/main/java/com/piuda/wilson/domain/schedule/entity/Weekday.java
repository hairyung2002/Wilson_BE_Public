package com.piuda.wilson.domain.schedule.entity;

import java.time.DayOfWeek;

/** API에서 쓰는 요일 표기 ("MON"~"SUN", api-spec 11장). */
public enum Weekday {
    MON(DayOfWeek.MONDAY),
    TUE(DayOfWeek.TUESDAY),
    WED(DayOfWeek.WEDNESDAY),
    THU(DayOfWeek.THURSDAY),
    FRI(DayOfWeek.FRIDAY),
    SAT(DayOfWeek.SATURDAY),
    SUN(DayOfWeek.SUNDAY);

    private final DayOfWeek dayOfWeek;

    Weekday(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public DayOfWeek toDayOfWeek() {
        return dayOfWeek;
    }

    public static Weekday from(DayOfWeek dayOfWeek) {
        return values()[dayOfWeek.ordinal()];
    }
}
