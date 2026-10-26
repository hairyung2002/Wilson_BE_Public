package com.piuda.wilson.domain.member.controller;

import com.piuda.wilson.domain.member.dto.ChangePasswordRequest;
import com.piuda.wilson.domain.member.dto.MemberResponse;
import com.piuda.wilson.domain.member.dto.UpdateMemberRequest;
import com.piuda.wilson.domain.member.service.UserService;
import com.piuda.wilson.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<MemberResponse> getMe(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(userService.getMe(memberId));
    }

    @PatchMapping("/me")
    public ApiResponse<MemberResponse> updateMe(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody UpdateMemberRequest request) {
        return ApiResponse.success(userService.updateMe(memberId, request));
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(memberId, request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal Long memberId) {
        userService.withdraw(memberId);
    }
}
