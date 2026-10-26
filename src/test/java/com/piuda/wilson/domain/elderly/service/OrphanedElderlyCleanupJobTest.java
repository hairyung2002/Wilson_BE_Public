package com.piuda.wilson.domain.elderly.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrphanedElderlyCleanupJobTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private ElderlyDeletionService elderlyDeletionService;

    @Mock
    private SchedulerLock schedulerLock;

    private OrphanedElderlyCleanupJob job;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T19:00:00Z"), KST);
        job = new OrphanedElderlyCleanupJob(elderlyRepository, elderlyDeletionService, schedulerLock, clock);
    }

    @Test
    @DisplayName("보호자 없이 30일이 지난 노인을 하나씩 파기한다")
    void cleanUp() {
        // given
        given(schedulerLock.tryLock(eq("orphaned-elderly-cleanup"), any())).willReturn(true);
        given(elderlyRepository.findOrphanedIdsBefore(any())).willReturn(List.of(15L, 16L));

        // when
        job.cleanUp();

        // then
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(elderlyRepository).findOrphanedIdsBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 21, 4, 0));
        verify(elderlyDeletionService).erase(15L);
        verify(elderlyDeletionService).erase(16L);
    }

    @Test
    @DisplayName("한 명을 지우다 실패해도 나머지는 계속 지운다 (다음 날 다시 시도)")
    void cleanUp_continuesOnFailure() {
        // given
        given(schedulerLock.tryLock(eq("orphaned-elderly-cleanup"), any())).willReturn(true);
        given(elderlyRepository.findOrphanedIdsBefore(any())).willReturn(List.of(15L, 16L));
        willThrow(new IllegalStateException("db")).given(elderlyDeletionService).erase(15L);

        // when
        job.cleanUp();

        // then
        verify(elderlyDeletionService).erase(16L);
    }

    @Test
    @DisplayName("다른 Pod가 이미 실행 중이면 아무것도 하지 않는다")
    void cleanUp_lockedByOtherPod() {
        // given
        given(schedulerLock.tryLock(eq("orphaned-elderly-cleanup"), any())).willReturn(false);

        // when
        job.cleanUp();

        // then
        verify(elderlyRepository, never()).findOrphanedIdsBefore(any());
    }
}
