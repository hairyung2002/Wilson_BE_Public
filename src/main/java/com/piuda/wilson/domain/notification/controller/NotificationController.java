package com.piuda.wilson.domain.notification.controller;

import com.piuda.wilson.domain.notification.dto.NotificationResponse;
import com.piuda.wilson.domain.notification.dto.NotificationSettingsRequest;
import com.piuda.wilson.domain.notification.dto.NotificationSettingsResponse;
import com.piuda.wilson.domain.notification.dto.UnreadCountResponse;
import com.piuda.wilson.domain.notification.service.NotificationService;
import com.piuda.wilson.domain.notification.service.NotificationSettingsService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSettingsService notificationSettingsService;

    @GetMapping("/api/v1/notifications")
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size) {
        return ApiResponse.success(notificationService.getNotifications(memberId, PageRequest.of(page, size)));
    }

    @PatchMapping("/api/v1/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Long memberId, @PathVariable Long notificationId) {
        notificationService.markRead(memberId, notificationId);
    }

    @PatchMapping("/api/v1/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal Long memberId) {
        notificationService.markAllRead(memberId);
    }

    @GetMapping("/api/v1/notifications/unread-count")
    public ApiResponse<UnreadCountResponse> countUnread(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(notificationService.countUnread(memberId));
    }

    @GetMapping("/api/v1/elderlies/{elderlyId}/notification-settings")
    public ApiResponse<NotificationSettingsResponse> getSettings(@AuthenticationPrincipal Long memberId,
                                                                 @PathVariable Long elderlyId) {
        return ApiResponse.success(notificationSettingsService.get(memberId, elderlyId));
    }

    @PatchMapping("/api/v1/elderlies/{elderlyId}/notification-settings")
    public ApiResponse<NotificationSettingsResponse> updateSettings(@AuthenticationPrincipal Long memberId,
                                                                    @PathVariable Long elderlyId,
                                                                    @RequestBody NotificationSettingsRequest request) {
        return ApiResponse.success(notificationSettingsService.update(memberId, elderlyId, request));
    }
}
