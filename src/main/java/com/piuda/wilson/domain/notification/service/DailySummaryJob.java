package com.piuda.wilson.domain.notification.service;

import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.global.scheduler.SchedulerLock;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 20:00(KST) 보호자에게 하루 대화 요약을 보낸다 (결정 E2 DAILY_SUMMARY).
 * 대화 원문이 아니라 횟수·시간만 담는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailySummaryJob {

    private static final String JOB_NAME = "daily-summary";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final CareRelationshipRepository careRelationshipRepository;
    private final ConversationRepository conversationRepository;
    private final ElderlyRepository elderlyRepository;
    private final NotificationService notificationService;
    private final SchedulerLock schedulerLock;
    private final Clock clock;

    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    public void summarize() {
        if (!schedulerLock.tryLock(JOB_NAME, LOCK_TTL)) {
            return;
        }
        LocalDateTime today = LocalDate.now(clock).atStartOfDay();
        for (Long elderlyId : careRelationshipRepository.findActiveElderlyIds()) {
            try {
                elderlyRepository.findByIdAndDeletedFalse(elderlyId)
                        .ifPresent(elderly -> summarize(elderly, today));
            } catch (RuntimeException e) {
                log.error("Daily summary failed: elderlyId={}", elderlyId, e);
            }
        }
    }

    private void summarize(Elderly elderly, LocalDateTime today) {
        List<Conversation> conversations = conversationRepository
                .findByElderlyIdAndDeletedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        elderly.getId(), today, today.plusDays(1));
        notificationService.notifyCaregivers(elderly.getId(), NotificationType.DAILY_SUMMARY,
                elderly.getName() + "님의 오늘", body(conversations));
    }

    private static String body(List<Conversation> conversations) {
        if (conversations.isEmpty()) {
            return "오늘은 대화가 없었어요";
        }
        long minutes = conversations.stream().mapToLong(Conversation::minutesTalked).sum();
        return "오늘 대화 " + conversations.size() + "번, " + minutes + "분 나누셨어요";
    }
}
