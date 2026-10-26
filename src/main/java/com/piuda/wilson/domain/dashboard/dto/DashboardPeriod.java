package com.piuda.wilson.domain.dashboard.dto;

/** 대시보드 추이 기간 (api-spec 13). 오늘을 포함한 최근 N일. */
public enum DashboardPeriod {

    WEEK(7),
    MONTH(30);

    private final int days;

    DashboardPeriod(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }
}
