package com.piuda.wilson.domain.member.controller;

import com.piuda.wilson.domain.member.dto.CreateMemberRequest;
import com.piuda.wilson.domain.member.dto.CreateMemberResponse;
import com.piuda.wilson.domain.member.service.MemberService;
import com.piuda.wilson.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateMemberResponse> signUp(@Valid @RequestBody CreateMemberRequest request) {
        return ApiResponse.success(memberService.create(request));
    }
}
