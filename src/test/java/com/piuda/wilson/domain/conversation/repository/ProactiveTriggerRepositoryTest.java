package com.piuda.wilson.domain.conversation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.conversation.entity.ProactiveStatus;
import com.piuda.wilson.domain.conversation.entity.ProactiveTrigger;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.schedule.entity.Schedule;
import com.piuda.wilson.domain.schedule.entity.ScheduleAuthor;
import com.piuda.wilson.domain.schedule.entity.ScheduleType;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ProactiveTriggerRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 50);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProactiveTriggerRepository proactiveTriggerRepository;

    @Test
    @DisplayName("응답을 기다린 지 10분이 지난 트리거와 대화로 연결된 트리거를 찾는다")
    void findForFollowUp() {
        // given
        Elderly elderly = entityManager.persist(Elderly.builder().name("김철수").build());
        Schedule schedule = entityManager.persist(Schedule.builder().elderly(elderly).type(ScheduleType.MEAL)
                .time(LocalTime.of(8, 30)).daysOfWeek(EnumSet.of(DayOfWeek.TUESDAY)).enabled(true)
                .createdBy(ScheduleAuthor.CAREGIVER).build());
        ProactiveTrigger old = proactiveTriggerRepository.save(ProactiveTrigger.create(schedule, NOW.minusMinutes(20), "어르신, 오늘은 기분이 어떠세요?"));
        ProactiveTrigger recent = proactiveTriggerRepository.save(ProactiveTrigger.create(schedule, NOW.minusMinutes(5), "어르신, 오늘은 기분이 어떠세요?"));
        recent.attachConversation(100L);
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThat(proactiveTriggerRepository.findByStatusAndSentAtLessThanEqual(ProactiveStatus.SENT,
                NOW.minusMinutes(10))).extracting(ProactiveTrigger::getId).containsExactly(old.getId());
        assertThat(proactiveTriggerRepository.findByConversationId(100L))
                .hasValueSatisfying(trigger -> assertThat(trigger.getId()).isEqualTo(recent.getId()));
        assertThat(proactiveTriggerRepository.findById(old.getId()).orElseThrow().getElderly().getId())
                .isEqualTo(elderly.getId());
    }

    @Test
    @DisplayName("대시보드용: 그날 예정됐던 선제 대화를 상태별로 센다")
    void countByStatusOnDay() {
        // given
        Elderly elderly = entityManager.persist(Elderly.builder().name("김철수").build());
        Schedule schedule = entityManager.persist(Schedule.builder().elderly(elderly).type(ScheduleType.MEAL)
                .time(LocalTime.of(8, 30)).daysOfWeek(EnumSet.of(DayOfWeek.TUESDAY)).enabled(true)
                .createdBy(ScheduleAuthor.CAREGIVER).build());
        LocalDateTime midnight = NOW.toLocalDate().atStartOfDay();
        proactiveTriggerRepository.save(ProactiveTrigger.create(schedule, midnight.plusHours(8), "어르신, 오늘은 기분이 어떠세요?")).markResponded();
        proactiveTriggerRepository.save(ProactiveTrigger.create(schedule, midnight.plusHours(12), "어르신, 오늘은 기분이 어떠세요?")).markResponded();
        proactiveTriggerRepository.save(ProactiveTrigger.create(schedule, midnight.plusHours(18), "어르신, 오늘은 기분이 어떠세요?")).markMissed();
        proactiveTriggerRepository.save(ProactiveTrigger.create(schedule, midnight.minusHours(1), "어르신, 오늘은 기분이 어떠세요?")).markResponded();
        entityManager.flush();

        // when & then
        assertThat(proactiveTriggerRepository.countByElderlyIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                elderly.getId(), ProactiveStatus.RESPONDED, midnight, midnight.plusDays(1))).isEqualTo(2);
        assertThat(proactiveTriggerRepository.countByElderlyIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                elderly.getId(), ProactiveStatus.MISSED, midnight, midnight.plusDays(1))).isEqualTo(1);
    }
}
