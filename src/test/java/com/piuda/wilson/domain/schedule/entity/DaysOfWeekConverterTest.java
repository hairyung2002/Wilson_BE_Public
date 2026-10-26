package com.piuda.wilson.domain.schedule.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DaysOfWeekConverterTest {

    private final DaysOfWeekConverter converter = new DaysOfWeekConverter();

    @Test
    @DisplayName("요일 집합을 월요일부터 순서대로 'MON,WED' 형태로 저장한다")
    void toDatabase() {
        // when & then
        assertThat(converter.convertToDatabaseColumn(EnumSet.of(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)))
                .isEqualTo("MON,WED");
        assertThat(converter.convertToDatabaseColumn(EnumSet.allOf(DayOfWeek.class)))
                .isEqualTo("MON,TUE,WED,THU,FRI,SAT,SUN");
    }

    @Test
    @DisplayName("저장된 문자열을 요일 집합으로 되돌린다")
    void toEntity() {
        // when & then
        assertThat(converter.convertToEntityAttribute("SUN,MON")).containsExactlyInAnyOrder(DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY);
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }
}
