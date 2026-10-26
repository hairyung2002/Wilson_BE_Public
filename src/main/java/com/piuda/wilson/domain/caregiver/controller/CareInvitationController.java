package com.piuda.wilson.domain.caregiver.controller;

import com.piuda.wilson.domain.caregiver.dto.InviteCodeResponse;
import com.piuda.wilson.domain.caregiver.dto.JoinCareRelationshipRequest;
import com.piuda.wilson.domain.caregiver.dto.JoinCareRelationshipResponse;
import com.piuda.wilson.domain.caregiver.service.CareInvitationService;
import com.piuda.wilson.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CareInvitationController {

    private final CareInvitationService careInvitationService;

    @PostMapping("/api/v1/elderlies/{elderlyId}/invite-code")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InviteCodeResponse> issueInviteCode(@AuthenticationPrincipal Long memberId,
                                                           @PathVariable Long elderlyId) {
        return ApiResponse.success(careInvitationService.issueInviteCode(memberId, elderlyId));
    }

    @PostMapping("/api/v1/care-relationships")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JoinCareRelationshipResponse> join(@AuthenticationPrincipal Long memberId,
                                                          @Valid @RequestBody JoinCareRelationshipRequest request) {
        return ApiResponse.success(careInvitationService.join(memberId, request));
    }
}
