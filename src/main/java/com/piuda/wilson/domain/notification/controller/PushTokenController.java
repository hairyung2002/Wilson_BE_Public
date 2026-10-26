package com.piuda.wilson.domain.notification.controller;

import com.piuda.wilson.domain.notification.dto.RegisterPushTokenRequest;
import com.piuda.wilson.domain.notification.service.PushTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @PutMapping("/api/v1/users/me/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@AuthenticationPrincipal Long memberId,
                         @Valid @RequestBody RegisterPushTokenRequest request) {
        pushTokenService.register(memberId, request);
    }
}
