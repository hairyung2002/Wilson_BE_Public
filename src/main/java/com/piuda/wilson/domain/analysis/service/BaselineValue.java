package com.piuda.wilson.domain.analysis.service;

import java.time.LocalDate;

/** 지표 하나의 기준 평균·표준편차와 그 값을 만든 기간 (api-spec 12-2). */
public record BaselineValue(double mean, double std, LocalDate periodStart, LocalDate periodEnd) {
}
