package com.piuda.wilson.domain.auth.controller;

import com.piuda.wilson.domain.auth.dto.DeviceLoginRequest;
import com.piuda.wilson.domain.auth.dto.LoginRequest;
import com.piuda.wilson.domain.auth.dto.LoginResponse;
import com.piuda.wilson.domain.auth.dto.ReissueRequest;
import com.piuda.wilson.domain.auth.dto.ReissueResponse;
import com.piuda.wilson.domain.auth.service.AuthService;
import com.piuda.wilson.global.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/device-login")
    public ApiResponse<LoginResponse> deviceLogin(@Valid @RequestBody DeviceLoginRequest request,
                                                  HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.deviceLogin(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/reissue")
    public ApiResponse<ReissueResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ApiResponse.success(authService.reissue(request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Long memberId) {
        authService.logout(memberId);
    }
}
