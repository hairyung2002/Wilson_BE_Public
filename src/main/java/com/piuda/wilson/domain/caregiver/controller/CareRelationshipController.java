package com.piuda.wilson.domain.caregiver.controller;

import com.piuda.wilson.domain.caregiver.dto.CaregiverResponse;
import com.piuda.wilson.domain.caregiver.dto.TransferOwnerRequest;
import com.piuda.wilson.domain.caregiver.service.CareRelationshipService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CareRelationshipController {

    private final CareRelationshipService careRelationshipService;

    @GetMapping("/api/v1/elderlies/{elderlyId}/caregivers")
    public ApiResponse<List<CaregiverResponse>> getCaregivers(@AuthenticationPrincipal Long memberId,
                                                              @PathVariable Long elderlyId) {
        return ApiResponse.success(careRelationshipService.getCaregivers(memberId, elderlyId));
    }

    @PatchMapping("/api/v1/elderlies/{elderlyId}/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferOwner(@AuthenticationPrincipal Long memberId, @PathVariable Long elderlyId,
                              @Valid @RequestBody TransferOwnerRequest request) {
        careRelationshipService.transferOwner(memberId, elderlyId, request.careRelationshipId());
    }

    @DeleteMapping("/api/v1/care-relationships/{careRelationshipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@AuthenticationPrincipal Long memberId, @PathVariable Long careRelationshipId) {
        careRelationshipService.unlink(memberId, careRelationshipId);
    }
}
