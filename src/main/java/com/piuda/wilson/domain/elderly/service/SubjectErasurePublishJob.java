package com.piuda.wilson.domain.elderly.service;

import com.piuda.wilson.domain.elderly.entity.SubjectErasure;
import com.piuda.wilson.domain.elderly.repository.SubjectErasureRepository;
import com.piuda.wilson.global.scheduler.SchedulerLock;
import com.piuda.wilson.infrastructure.ai.AiEventPublisher;
import com.piuda.wilson.infrastructure.ai.SubjectErasureEvent;
import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다 남은 파기 요청을 AI로 보낸다 (결정 J3). 브로커가 받은 요청만 지우므로 Kafka가 내려가 있어도
 * 요청은 사라지지 않는다. 같은 요청이 두 번 갈 수 있으며, AI 쪽 파기는 여러 번 해도 결과가 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubjectErasurePublishJob {

    private static final String JOB_NAME = "subject-erasure-publish";
    private static final Duration LOCK_TTL = Duration.ofSeconds(50);

    private final SubjectErasureRepository subjectErasureRepository;
    private final AiEventPublisher aiEventPublisher;
    private final SchedulerLock schedulerLock;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void publish() {
        if (!schedulerLock.tryLock(JOB_NAME, LOCK_TTL)) {
            return;
        }
        for (SubjectErasure request : subjectErasureRepository.findTop100ByOrderByIdAsc()) {
            if (!aiEventPublisher.publishSubjectErasure(toEvent(request))) {
                log.warn("Subject erasure publish failed, will retry: requestId={}", request.getId());
                return;
            }
            subjectErasureRepository.delete(request);
            log.info("Subject erasure published: requestId={}", request.getId());
        }
    }

    private static SubjectErasureEvent toEvent(SubjectErasure request) {
        return new SubjectErasureEvent(UUID.randomUUID().toString(), request.getSubjectId().toString(),
                request.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
    }
}
