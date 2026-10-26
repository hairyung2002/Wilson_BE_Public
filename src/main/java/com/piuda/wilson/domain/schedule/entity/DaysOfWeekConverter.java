package com.piuda.wilson.domain.schedule.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** 요일 집합 ↔ "MON,WED" 문자열. 월요일부터 순서대로 저장한다. */
@Converter
public class DaysOfWeekConverter implements AttributeConverter<Set<DayOfWeek>, String> {

    @Override
    public String convertToDatabaseColumn(Set<DayOfWeek> days) {
        return EnumSet.copyOf(days).stream()
                .map(day -> Weekday.from(day).name())
                .collect(Collectors.joining(","));
    }

    @Override
    public Set<DayOfWeek> convertToEntityAttribute(String value) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (value == null || value.isBlank()) {
            return days;
        }
        Arrays.stream(value.split(",")).map(day -> Weekday.valueOf(day.trim()).toDayOfWeek()).forEach(days::add);
        return days;
    }
}
