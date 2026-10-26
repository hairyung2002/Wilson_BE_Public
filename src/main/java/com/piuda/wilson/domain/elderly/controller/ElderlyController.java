package com.piuda.wilson.domain.elderly.controller;

import com.piuda.wilson.domain.elderly.dto.CreateElderlyRequest;
import com.piuda.wilson.domain.elderly.dto.CreateElderlyResponse;
import com.piuda.wilson.domain.elderly.dto.DeviceCodeResponse;
import com.piuda.wilson.domain.elderly.dto.ElderlyDetailResponse;
import com.piuda.wilson.domain.elderly.dto.ElderlySummaryResponse;
import com.piuda.wilson.domain.elderly.dto.UpdateElderlyRequest;
import com.piuda.wilson.domain.elderly.service.ElderlyService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/elderlies")
public class ElderlyController {

    private final ElderlyService elderlyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateElderlyResponse> register(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody CreateElderlyRequest request) {
        return ApiResponse.success(elderlyService.register(memberId, request));
    }

    @GetMapping
    public ApiResponse<List<ElderlySummaryResponse>> getElderlies(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(elderlyService.getElderlies(memberId));
    }

    @GetMapping("/{elderlyId}")
    public ApiResponse<ElderlyDetailResponse> getElderly(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long elderlyId) {
        return ApiResponse.success(elderlyService.getElderly(memberId, elderlyId));
    }

    @PatchMapping("/{elderlyId}")
    public ApiResponse<ElderlyDetailResponse> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long elderlyId,
            @Valid @RequestBody UpdateElderlyRequest request) {
        return ApiResponse.success(elderlyService.update(memberId, elderlyId, request));
    }

    @PostMapping("/{elderlyId}/device-code")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeviceCodeResponse> issueDeviceCode(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long elderlyId) {
        return ApiResponse.success(elderlyService.issueDeviceCode(memberId, elderlyId));
    }

    @DeleteMapping("/{elderlyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long elderlyId) {
        elderlyService.delete(memberId, elderlyId);
    }
}
