package com.piuda.wilson.domain.analysis.controller;

import com.piuda.wilson.domain.analysis.dto.AssessmentResponse;
import com.piuda.wilson.domain.analysis.service.AssessmentQueryService;
import com.piuda.wilson.global.common.response.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentQueryService assessmentQueryService;

    @GetMapping("/api/v1/elderlies/{elderlyId}/assessments")
    public ApiResponse<List<AssessmentResponse>> getAssessments(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long elderlyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(assessmentQueryService.getAssessments(memberId, elderlyId, from, to));
    }
}
