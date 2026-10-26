package com.piuda.wilson.domain.elderly.controller;

import com.piuda.wilson.domain.elderly.dto.ElderlySettingsRequest;
import com.piuda.wilson.domain.elderly.dto.ElderlySettingsResponse;
import com.piuda.wilson.domain.elderly.service.ElderlySettingsService;
import com.piuda.wilson.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/elderlies/{elderlyId}/settings")
public class ElderlySettingsController {

    private final ElderlySettingsService elderlySettingsService;

    @GetMapping
    public ApiResponse<ElderlySettingsResponse> get(@AuthenticationPrincipal Long memberId,
                                                    @PathVariable Long elderlyId) {
        return ApiResponse.success(elderlySettingsService.get(memberId, elderlyId));
    }

    @PatchMapping
    public ApiResponse<ElderlySettingsResponse> update(@AuthenticationPrincipal Long memberId,
                                                       @PathVariable Long elderlyId,
                                                       @RequestBody ElderlySettingsRequest request) {
        return ApiResponse.success(elderlySettingsService.update(memberId, elderlyId, request));
    }
}
