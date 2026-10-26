package com.piuda.wilson.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.service.AccessActor;
import com.piuda.wilson.domain.elderly.service.ElderlyAccess;
import com.piuda.wilson.domain.elderly.service.ElderlyAccessService;
import com.piuda.wilson.domain.schedule.dto.CreateScheduleRequest;
import com.piuda.wilson.domain.schedule.dto.ScheduleResponse;
import com.piuda.wilson.domain.schedule.dto.UpdateScheduleRequest;
import com.piuda.wilson.domain.schedule.entity.Schedule;
import com.piuda.wilson.domain.schedule.entity.ScheduleAuthor;
import com.piuda.wilson.domain.schedule.entity.ScheduleType;
import com.piuda.wilson.domain.schedule.entity.Weekday;
import com.piuda.wilson.domain.schedule.repository.ScheduleRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ELDERLY_ID = 15L;
    private static final LocalTime MORNING = LocalTime.of(8, 30);

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ElderlyAccessService elderlyAccessService;

    @InjectMocks
    private ScheduleService scheduleService;

    private final Elderly elderly = withId(Elderly.builder().name("김철수").build(), ELDERLY_ID);

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private Schedule existing(Long id, LocalTime time, DayOfWeek... days) {
        return withId(Schedule.builder().elderly(elderly).type(ScheduleType.MEAL).time(time)
                .daysOfWeek(EnumSet.of(days[0], days)).enabled(true).createdBy(ScheduleAuthor.CAREGIVER).build(), id);
    }

    private void givenViewer(AccessActor actor) {
        given(elderlyAccessService.requireViewer(MEMBER_ID, ELDERLY_ID)).willReturn(new ElderlyAccess(elderly, actor));
    }

    private static CreateScheduleRequest request(LocalTime time, Weekday... days) {
        return new CreateScheduleRequest(ScheduleType.MEDICATION, "혈압약", time, List.of(days), null);
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    @Test
    @DisplayName("보호자가 일정을 등록하면 켜진 상태로, 등록자는 보호자로 저장한다")
    void create_byCaregiver() {
        // given
        givenViewer(AccessActor.MEMBER);
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 3L));

        // when
        Long scheduleId = scheduleService.create(MEMBER_ID, ELDERLY_ID, request(MORNING, Weekday.MON, Weekday.WED))
                .scheduleId();

        // then
        assertThat(scheduleId).isEqualTo(3L);
        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        Schedule saved = captor.getValue();
        assertThat(saved.getCreatedBy()).isEqualTo(ScheduleAuthor.CAREGIVER);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getDaysOfWeek()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
    }

    @Test
    @DisplayName("노인 본인도 일정을 등록할 수 있다 (등록자는 노인)")
    void create_bySelf() {
        // given
        givenViewer(AccessActor.SELF);
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 3L));

        // when
        scheduleService.create(MEMBER_ID, ELDERLY_ID, request(MORNING, Weekday.MON));

        // then
        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(ScheduleAuthor.ELDERLY);
    }

    @Test
    @DisplayName("같은 시각에 요일이 겹치는 일정이 있으면 등록할 수 없다 (같은 시각 선제 대화 두 개 방지)")
    void create_conflict() {
        // given
        givenViewer(AccessActor.OWNER);
        given(scheduleRepository.findByElderlyIdOrderByTimeAsc(ELDERLY_ID))
                .willReturn(List.of(existing(7L, MORNING, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)));

        // when & then
        assertThatThrownBy(() -> scheduleService.create(MEMBER_ID, ELDERLY_ID, request(MORNING, Weekday.MON, Weekday.WED)))
                .satisfies(e -> assertErrorCode(e, ErrorCode.DUPLICATE_SCHEDULE_TIME));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 시각이라도 요일이 겹치지 않으면 등록할 수 있다")
    void create_sameTimeOtherDays() {
        // given
        givenViewer(AccessActor.OWNER);
        given(scheduleRepository.findByElderlyIdOrderByTimeAsc(ELDERLY_ID))
                .willReturn(List.of(existing(7L, MORNING, DayOfWeek.SATURDAY)));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 3L));

        // when & then
        assertThat(scheduleService.create(MEMBER_ID, ELDERLY_ID, request(MORNING, Weekday.MON)).scheduleId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("요일을 중복해서 보내면 잘못된 요청이다")
    void create_duplicateDays() {
        // given
        givenViewer(AccessActor.OWNER);

        // when & then
        assertThatThrownBy(() -> scheduleService.create(MEMBER_ID, ELDERLY_ID, request(MORNING, Weekday.MON, Weekday.MON)))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    @DisplayName("일정 목록을 시각순으로 본다")
    void getSchedules() {
        // given
        givenViewer(AccessActor.SELF);
        given(scheduleRepository.findByElderlyIdOrderByTimeAsc(ELDERLY_ID))
                .willReturn(List.of(existing(7L, MORNING, DayOfWeek.MONDAY, DayOfWeek.SUNDAY)));

        // when
        List<ScheduleResponse> schedules = scheduleService.getSchedules(MEMBER_ID, ELDERLY_ID);

        // then
        assertThat(schedules).singleElement().satisfies(schedule -> {
            assertThat(schedule.scheduleId()).isEqualTo(7L);
            assertThat(schedule.daysOfWeek()).containsExactly(Weekday.MON, Weekday.SUN);
        });
    }

    @Test
    @DisplayName("보낸 항목만 고치고, 자기 자신과는 시각이 겹쳐도 충돌로 보지 않는다")
    void update() {
        // given
        Schedule schedule = existing(7L, MORNING, DayOfWeek.MONDAY);
        given(scheduleRepository.findById(7L)).willReturn(Optional.of(schedule));
        givenViewer(AccessActor.OWNER);
        given(scheduleRepository.findByElderlyIdOrderByTimeAsc(ELDERLY_ID)).willReturn(List.of(schedule));

        // when
        ScheduleResponse response = scheduleService.update(MEMBER_ID, 7L,
                new UpdateScheduleRequest(null, "점심", null, List.of(Weekday.MON, Weekday.TUE), false));

        // then
        assertThat(response.label()).isEqualTo("점심");
        assertThat(response.time()).isEqualTo(MORNING);
        assertThat(response.daysOfWeek()).containsExactly(Weekday.MON, Weekday.TUE);
        assertThat(response.enabled()).isFalse();
    }

    @Test
    @DisplayName("다른 일정과 시각·요일이 겹치게 고칠 수는 없다")
    void update_conflict() {
        // given
        Schedule schedule = existing(7L, MORNING, DayOfWeek.MONDAY);
        given(scheduleRepository.findById(7L)).willReturn(Optional.of(schedule));
        givenViewer(AccessActor.OWNER);
        given(scheduleRepository.findByElderlyIdOrderByTimeAsc(ELDERLY_ID))
                .willReturn(List.of(schedule, existing(8L, LocalTime.of(12, 0), DayOfWeek.MONDAY)));

        // when & then
        assertThatThrownBy(() -> scheduleService.update(MEMBER_ID, 7L,
                new UpdateScheduleRequest(null, null, LocalTime.of(12, 0), null, null)))
                .satisfies(e -> assertErrorCode(e, ErrorCode.DUPLICATE_SCHEDULE_TIME));
        assertThat(schedule.getTime()).isEqualTo(MORNING);
    }

    @Test
    @DisplayName("일정을 지운다. 없는 일정은 404")
    void delete() {
        // given
        Schedule schedule = existing(7L, MORNING, DayOfWeek.MONDAY);
        given(scheduleRepository.findById(7L)).willReturn(Optional.of(schedule));
        given(scheduleRepository.findById(8L)).willReturn(Optional.empty());
        givenViewer(AccessActor.SELF);

        // when
        scheduleService.delete(MEMBER_ID, 7L);

        // then
        verify(scheduleRepository).delete(schedule);
        assertThatThrownBy(() -> scheduleService.delete(MEMBER_ID, 8L))
                .satisfies(e -> assertErrorCode(e, ErrorCode.SCHEDULE_NOT_FOUND));
    }
}
