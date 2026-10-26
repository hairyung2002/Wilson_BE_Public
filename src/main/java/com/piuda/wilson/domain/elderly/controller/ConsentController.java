package com.piuda.wilson.domain.elderly.controller;

import com.piuda.wilson.domain.elderly.dto.ConsentResponse;
import com.piuda.wilson.domain.elderly.dto.UpdateConsentRequest;
import com.piuda.wilson.domain.elderly.entity.ConsentType;
import com.piuda.wilson.domain.elderly.service.ConsentService;
import com.piuda.wilson.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/elderlies/{elderlyId}/consents")
public class ConsentController {

    private final ConsentService consentService;

    @GetMapping
    public ApiResponse<List<ConsentResponse>> getConsents(@AuthenticationPrincipal Long memberId,
                                                          @PathVariable Long elderlyId) {
        return ApiResponse.success(consentService.getConsents(memberId, elderlyId));
    }

    @PutMapping("/{type}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateConsent(@AuthenticationPrincipal Long memberId, @PathVariable Long elderlyId,
                              @PathVariable ConsentType type, @Valid @RequestBody UpdateConsentRequest request) {
        consentService.updateConsent(memberId, elderlyId, type, request);
    }
}
