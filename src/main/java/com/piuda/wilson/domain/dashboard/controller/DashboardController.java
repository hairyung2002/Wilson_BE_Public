package com.piuda.wilson.domain.dashboard.controller;

import com.piuda.wilson.domain.dashboard.dto.DashboardPeriod;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse;
import com.piuda.wilson.domain.dashboard.service.DashboardService;
import com.piuda.wilson.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/v1/elderlies/{elderlyId}/dashboard")
    public ApiResponse<DashboardResponse> getDashboard(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long elderlyId,
            @RequestParam(defaultValue = "WEEK") DashboardPeriod period) {
        return ApiResponse.success(dashboardService.getDashboard(memberId, elderlyId, period));
    }
}
