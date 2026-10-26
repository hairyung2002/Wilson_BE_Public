package com.piuda.wilson.domain.elderly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.elderly.entity.SubjectErasure;
import com.piuda.wilson.domain.elderly.repository.SubjectErasureRepository;
import com.piuda.wilson.global.scheduler.SchedulerLock;
import com.piuda.wilson.infrastructure.ai.AiEventPublisher;
import com.piuda.wilson.infrastructure.ai.SubjectErasureEvent;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubjectErasurePublishJobTest {

    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 22, 12, 0);

    @Mock
    private SubjectErasureRepository subjectErasureRepository;

    @Mock
    private AiEventPublisher aiEventPublisher;

    @Mock
    private SchedulerLock schedulerLock;

    @InjectMocks
    private SubjectErasurePublishJob job;

    private static SubjectErasure request() {
        SubjectErasure request = SubjectErasure.of(UUID.randomUUID());
        ReflectionTestUtils.setField(request, "createdAt", REQUESTED_AT);
        return request;
    }

    @Test
    @DisplayName("남은 파기 요청을 AI로 보내고, 브로커가 받은 요청은 지운다")
    void publish() {
        // given
        SubjectErasure first = request();
        SubjectErasure second = request();
        given(schedulerLock.tryLock(any(), any())).willReturn(true);
        given(subjectErasureRepository.findTop100ByOrderByIdAsc()).willReturn(List.of(first, second));
        given(aiEventPublisher.publishSubjectErasure(any())).willReturn(true);

        // when
        job.publish();

        // then
        ArgumentCaptor<SubjectErasureEvent> events = ArgumentCaptor.forClass(SubjectErasureEvent.class);
        verify(aiEventPublisher, times(2)).publishSubjectErasure(events.capture());
        assertThat(events.getAllValues()).extracting(SubjectErasureEvent::subjectId)
                .containsExactly(first.getSubjectId().toString(), second.getSubjectId().toString());
        assertThat(events.getValue().requestedAt()).isEqualTo(REQUESTED_AT.atZone(ZoneId.systemDefault()).toInstant());
        verify(subjectErasureRepository).delete(first);
        verify(subjectErasureRepository).delete(second);
    }

    @Test
    @DisplayName("발행이 실패하면 요청을 남겨 다음 회차에 다시 보내고, 브로커 장애로 보고 이번 회차는 멈춘다")
    void publish_failed() {
        // given
        SubjectErasure first = request();
        given(schedulerLock.tryLock(any(), any())).willReturn(true);
        given(subjectErasureRepository.findTop100ByOrderByIdAsc()).willReturn(List.of(first, request()));
        given(aiEventPublisher.publishSubjectErasure(any())).willReturn(false);

        // when
        job.publish();

        // then
        verify(aiEventPublisher, times(1)).publishSubjectErasure(any());
        verify(subjectErasureRepository, never()).delete(any());
    }

    @Test
    @DisplayName("다른 Pod가 실행 중이면 건너뛴다")
    void publish_locked() {
        // given
        given(schedulerLock.tryLock(any(), any())).willReturn(false);

        // when
        job.publish();

        // then
        verify(subjectErasureRepository, never()).findTop100ByOrderByIdAsc();
    }
}
