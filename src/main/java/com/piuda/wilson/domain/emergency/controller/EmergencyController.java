package com.piuda.wilson.domain.emergency.controller;

import com.piuda.wilson.domain.emergency.dto.EmergencyResponse;
import com.piuda.wilson.domain.emergency.service.EmergencyService;
import com.piuda.wilson.global.common.response.ApiResponse;
import com.piuda.wilson.global.common.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EmergencyController {

    private final EmergencyService emergencyService;

    @GetMapping("/api/v1/elderlies/{elderlyId}/emergencies")
    public ApiResponse<PageResponse<EmergencyResponse>> getEmergencies(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long elderlyId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size) {
        return ApiResponse.success(emergencyService.getEmergencies(memberId, elderlyId, PageRequest.of(page, size)));
    }

    @PatchMapping("/api/v1/emergencies/{emergencyId}/acknowledge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acknowledge(@AuthenticationPrincipal Long memberId, @PathVariable Long emergencyId) {
        emergencyService.acknowledge(memberId, emergencyId);
    }
}
