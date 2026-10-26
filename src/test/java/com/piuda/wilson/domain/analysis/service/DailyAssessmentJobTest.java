package com.piuda.wilson.domain.analysis.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.global.scheduler.SchedulerLock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyAssessmentJobTest {

    /** 2026-10-21 03:00 KST */
    private static final Clock DAWN = Clock.fixed(Instant.parse("2026-10-20T18:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 20);

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private SchedulerLock schedulerLock;

    private DailyAssessmentJob job;

    @BeforeEach
    void setUp() {
        job = new DailyAssessmentJob(elderlyRepository, assessmentService, schedulerLock, DAWN);
    }

    @Test
    @DisplayName("삭제되지 않은 노인마다 전날을 판정하고, 한 명이 실패해도 나머지는 계속한다")
    void assessYesterday() {
        // given
        given(schedulerLock.tryLock(eq("daily-assessment"), any())).willReturn(true);
        given(elderlyRepository.findActiveIds()).willReturn(List.of(15L, 16L));
        willThrow(new IllegalStateException("boom")).given(assessmentService).assess(15L, YESTERDAY);

        // when
        job.assess();

        // then
        verify(assessmentService).assess(16L, YESTERDAY);
    }

    @Test
    @DisplayName("다른 Pod가 실행 중이면 아무것도 하지 않는다")
    void locked() {
        // given
        given(schedulerLock.tryLock(eq("daily-assessment"), any())).willReturn(false);

        // when
        job.assess();

        // then
        verify(assessmentService, never()).assess(any(), any());
    }
}
