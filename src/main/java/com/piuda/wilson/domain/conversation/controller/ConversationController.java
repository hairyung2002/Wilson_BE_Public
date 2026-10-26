package com.piuda.wilson.domain.conversation.controller;

import com.piuda.wilson.domain.conversation.dto.ConversationDetailResponse;
import com.piuda.wilson.domain.conversation.dto.ConversationSummaryResponse;
import com.piuda.wilson.domain.conversation.dto.FinishConversationResponse;
import com.piuda.wilson.domain.conversation.dto.StartConversationRequest;
import com.piuda.wilson.domain.conversation.dto.StartConversationResponse;
import com.piuda.wilson.domain.conversation.dto.StartProactiveRequest;
import com.piuda.wilson.domain.conversation.service.ConversationService;
import com.piuda.wilson.domain.conversation.service.ProactiveService;
import com.piuda.wilson.global.common.response.ApiResponse;
import com.piuda.wilson.global.common.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ProactiveService proactiveService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StartConversationResponse> start(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody StartConversationRequest request) {
        return ApiResponse.success(conversationService.start(memberId, request));
    }

    @PostMapping("/proactive")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StartConversationResponse> startProactive(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody StartProactiveRequest request) {
        return ApiResponse.success(proactiveService.start(memberId, request.triggerId()));
    }

    @PatchMapping("/{conversationId}/finish")
    public ApiResponse<FinishConversationResponse> finish(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long conversationId) {
        return ApiResponse.success(conversationService.finish(memberId, conversationId));
    }

    @GetMapping
    public ApiResponse<PageResponse<ConversationSummaryResponse>> getConversations(
            @AuthenticationPrincipal Long memberId,
            @RequestParam Long elderlyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size) {
        return ApiResponse.success(
                conversationService.getConversations(memberId, elderlyId, date, PageRequest.of(page, size)));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationDetailResponse> getConversation(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long conversationId) {
        return ApiResponse.success(conversationService.getConversation(memberId, conversationId));
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long conversationId) {
        conversationService.delete(memberId, conversationId);
    }
}
