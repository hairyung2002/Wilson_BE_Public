package com.piuda.wilson.domain.schedule.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.elderly.controller.ElderlySettingsController;
import com.piuda.wilson.domain.elderly.dto.ElderlySettingsResponse;
import com.piuda.wilson.domain.elderly.service.ElderlySettingsService;
import com.piuda.wilson.domain.schedule.dto.CreateScheduleRequest;
import com.piuda.wilson.domain.schedule.dto.CreateScheduleResponse;
import com.piuda.wilson.domain.schedule.dto.ScheduleResponse;
import com.piuda.wilson.domain.schedule.entity.ScheduleAuthor;
import com.piuda.wilson.domain.schedule.entity.ScheduleType;
import com.piuda.wilson.domain.schedule.entity.Weekday;
import com.piuda.wilson.domain.schedule.service.ScheduleService;
import com.piuda.wilson.global.config.SecurityConfig;
import com.piuda.wilson.global.security.JwtTokenProvider;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({ScheduleController.class, ElderlySettingsController.class})
@Import(SecurityConfig.class)
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleService scheduleService;

    @MockitoBean
    private ElderlySettingsService elderlySettingsService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    private static RequestPostProcessor member() {
        return authentication(new UsernamePasswordAuthenticationToken(5L, null, List.of()));
    }

    @Test
    @DisplayName("일정을 등록하면 201과 일정 ID를 반환한다")
    void create() throws Exception {
        // given
        given(scheduleService.create(5L, 15L, new CreateScheduleRequest(ScheduleType.MEDICATION, "혈압약",
                LocalTime.of(8, 30), List.of(Weekday.MON, Weekday.FRI), true)))
                .willReturn(new CreateScheduleResponse(3L));

        // when & then
        mockMvc.perform(post("/api/v1/elderlies/15/schedules").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MEDICATION","label":"혈압약","time":"08:30","daysOfWeek":["MON","FRI"],"enabled":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduleId").value(3));
    }

    @Test
    @DisplayName("일정 종류가 아니거나 요일이 없으면 400")
    void create_invalid() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/elderlies/15/schedules").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MEMORY","time":"08:30","daysOfWeek":["MON"]}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/elderlies/15/schedules").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MEAL","time":"08:30","daysOfWeek":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("daysOfWeek"));
        verify(scheduleService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("일정 목록·수정·삭제")
    void listUpdateDelete() throws Exception {
        // given
        ScheduleResponse schedule = new ScheduleResponse(3L, ScheduleType.MEAL, "점심", LocalTime.of(12, 0),
                List.of(Weekday.MON), true, ScheduleAuthor.ELDERLY, null);
        given(scheduleService.getSchedules(5L, 15L)).willReturn(List.of(schedule));
        given(scheduleService.update(any(), any(), any())).willReturn(schedule);

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/schedules").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].time").value("12:00"))
                .andExpect(jsonPath("$.data[0].daysOfWeek[0]").value("MON"))
                .andExpect(jsonPath("$.data[0].createdBy").value("ELDERLY"));
        mockMvc.perform(patch("/api/v1/schedules/3").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"점심"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/schedules/3").with(member())).andExpect(status().isNoContent());
        verify(scheduleService).delete(5L, 3L);
    }

    @Test
    @DisplayName("노인 설정을 보고 바꾼다")
    void settings() throws Exception {
        // given
        ElderlySettingsResponse settings = new ElderlySettingsResponse(false, LocalTime.of(21, 0), LocalTime.of(7, 0));
        given(elderlySettingsService.get(5L, 15L)).willReturn(settings);
        given(elderlySettingsService.update(any(), any(), any())).willReturn(settings);

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/15/settings").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proactiveEnabled").value(false))
                .andExpect(jsonPath("$.data.quietHoursStart").value("21:00"));
        mockMvc.perform(patch("/api/v1/elderlies/15/settings").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proactiveEnabled":false,"quietHoursStart":"21:00"}
                                """))
                .andExpect(status().isOk());
    }
}
