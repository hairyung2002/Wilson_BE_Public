package com.piuda.wilson.domain.schedule.controller;

import com.piuda.wilson.domain.schedule.dto.CreateScheduleRequest;
import com.piuda.wilson.domain.schedule.dto.CreateScheduleResponse;
import com.piuda.wilson.domain.schedule.dto.ScheduleResponse;
import com.piuda.wilson.domain.schedule.dto.UpdateScheduleRequest;
import com.piuda.wilson.domain.schedule.service.ScheduleService;
import com.piuda.wilson.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping("/api/v1/elderlies/{elderlyId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateScheduleResponse> create(@AuthenticationPrincipal Long memberId,
                                                      @PathVariable Long elderlyId,
                                                      @Valid @RequestBody CreateScheduleRequest request) {
        return ApiResponse.success(scheduleService.create(memberId, elderlyId, request));
    }

    @GetMapping("/api/v1/elderlies/{elderlyId}/schedules")
    public ApiResponse<List<ScheduleResponse>> getSchedules(@AuthenticationPrincipal Long memberId,
                                                            @PathVariable Long elderlyId) {
        return ApiResponse.success(scheduleService.getSchedules(memberId, elderlyId));
    }

    @PatchMapping("/api/v1/schedules/{scheduleId}")
    public ApiResponse<ScheduleResponse> update(@AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId,
                                                @Valid @RequestBody UpdateScheduleRequest request) {
        return ApiResponse.success(scheduleService.update(memberId, scheduleId, request));
    }

    @DeleteMapping("/api/v1/schedules/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId) {
        scheduleService.delete(memberId, scheduleId);
    }
}
