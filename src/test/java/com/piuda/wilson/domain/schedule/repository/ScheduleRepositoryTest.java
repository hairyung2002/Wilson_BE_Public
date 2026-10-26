package com.piuda.wilson.domain.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.schedule.entity.Schedule;
import com.piuda.wilson.domain.schedule.entity.ScheduleAuthor;
import com.piuda.wilson.domain.schedule.entity.ScheduleType;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ScheduleRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ScheduleRepository scheduleRepository;

    private Schedule schedule(Elderly elderly, LocalTime time, boolean enabled) {
        return scheduleRepository.save(Schedule.builder().elderly(elderly).type(ScheduleType.MEDICATION).label("혈압약")
                .time(time).daysOfWeek(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)).enabled(enabled)
                .createdBy(ScheduleAuthor.CAREGIVER).build());
    }

    @Test
    @DisplayName("노인의 일정을 시각순으로 조회하고, 요일 집합을 그대로 되돌린다")
    void findByElderly() {
        // given
        Elderly elderly = entityManager.persist(Elderly.builder().name("김철수").build());
        schedule(elderly, LocalTime.of(19, 0), true);
        schedule(elderly, LocalTime.of(8, 30), true);
        entityManager.flush();
        entityManager.clear();

        // when
        List<Schedule> schedules = scheduleRepository.findByElderlyIdOrderByTimeAsc(elderly.getId());

        // then
        assertThat(schedules).extracting(Schedule::getTime).containsExactly(LocalTime.of(8, 30), LocalTime.of(19, 0));
        assertThat(schedules.get(0).getDaysOfWeek()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("지금 시각에 실행할 일정을 노인과 함께 찾는다 (꺼진 일정·삭제된 노인 제외)")
    void findDueAt() {
        // given
        Elderly active = entityManager.persist(Elderly.builder().name("김철수").build());
        Elderly deleted = entityManager.persist(Elderly.builder().name("이영희").build());
        deleted.delete();
        Schedule due = schedule(active, LocalTime.of(8, 30), true);
        schedule(active, LocalTime.of(8, 30), false);
        schedule(active, LocalTime.of(8, 31), true);
        schedule(deleted, LocalTime.of(8, 30), true);
        entityManager.flush();
        entityManager.clear();

        // when
        List<Schedule> schedules = scheduleRepository.findDueAt(LocalTime.of(8, 30));

        // then
        assertThat(schedules).extracting(Schedule::getId).containsExactly(due.getId());
        assertThat(Hibernate.isInitialized(schedules.get(0).getElderly())).isTrue();
    }
}
