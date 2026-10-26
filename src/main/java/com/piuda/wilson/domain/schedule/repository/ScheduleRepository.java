package com.piuda.wilson.domain.schedule.repository;

import com.piuda.wilson.domain.schedule.entity.Schedule;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByElderlyIdOrderByTimeAsc(Long elderlyId);

    /** 지금 시각에 걸 선제 대화 후보. 요일·노인 설정·방해 금지 시간은 서비스에서 거른다. */
    @Query("""
            select s from Schedule s
            join fetch s.elderly e
            where s.time = :time and s.enabled = true and e.deleted = false
            order by s.id
            """)
    List<Schedule> findDueAt(@Param("time") LocalTime time);

    void deleteByElderlyId(Long elderlyId);
}
