package com.piuda.wilson.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.global.scheduler.SchedulerLock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DailySummaryJobTest {

    private static final Clock EVENING = Clock.fixed(Instant.parse("2026-10-20T11:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime TODAY = LocalDateTime.of(2026, 10, 20, 0, 0);

    @Mock
    private CareRelationshipRepository careRelationshipRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SchedulerLock schedulerLock;

    private DailySummaryJob job;

    @BeforeEach
    void setUp() {
        job = new DailySummaryJob(careRelationshipRepository, conversationRepository, elderlyRepository,
                notificationService, schedulerLock, EVENING);
    }

    private static Conversation conversation(Elderly elderly, LocalDateTime startedAt, Integer minutes) {
        Conversation conversation = Conversation.builder().elderly(elderly).channel(ConversationChannel.VOICE)
                .type(ConversationType.DAILY).startedAt(startedAt).build();
        if (minutes != null) {
            conversation.finish(startedAt.plusMinutes(minutes));
        }
        return conversation;
    }

    @Test
    @DisplayName("보호자가 있는 노인마다 오늘 대화 횟수와 끝난 대화의 시간 합을 요약해 알린다")
    void summarize() {
        // given
        Elderly elderly = Elderly.builder().name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", 15L);
        given(schedulerLock.tryLock(eq("daily-summary"), any())).willReturn(true);
        given(careRelationshipRepository.findActiveElderlyIds()).willReturn(List.of(15L));
        given(elderlyRepository.findByIdAndDeletedFalse(15L)).willReturn(Optional.of(elderly));
        given(conversationRepository.findByElderlyIdAndDeletedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                15L, TODAY, TODAY.plusDays(1))).willReturn(List.of(
                conversation(elderly, TODAY.plusHours(9), 5),
                conversation(elderly, TODAY.plusHours(12), 7),
                conversation(elderly, TODAY.plusHours(19), null)));

        // when
        job.summarize();

        // then
        verify(notificationService).notifyCaregivers(15L, NotificationType.DAILY_SUMMARY,
                "김철수님의 오늘", "오늘 대화 3번, 12분 나누셨어요");
    }

    @Test
    @DisplayName("오늘 대화가 없었으면 그 사실을 알린다")
    void summarize_noConversation() {
        // given
        Elderly elderly = Elderly.builder().name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", 15L);
        given(schedulerLock.tryLock(eq("daily-summary"), any())).willReturn(true);
        given(careRelationshipRepository.findActiveElderlyIds()).willReturn(List.of(15L));
        given(elderlyRepository.findByIdAndDeletedFalse(15L)).willReturn(Optional.of(elderly));
        given(conversationRepository.findByElderlyIdAndDeletedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                15L, TODAY, TODAY.plusDays(1))).willReturn(List.of());

        // when
        job.summarize();

        // then
        verify(notificationService).notifyCaregivers(15L, NotificationType.DAILY_SUMMARY,
                "김철수님의 오늘", "오늘은 대화가 없었어요");
    }

    @Test
    @DisplayName("다른 Pod가 실행 중이면 아무것도 하지 않는다")
    void summarize_locked() {
        // given
        given(schedulerLock.tryLock(eq("daily-summary"), any())).willReturn(false);

        // when
        job.summarize();

        // then
        verify(notificationService, never()).notifyCaregivers(any(), any(), any(), any());
    }
}
