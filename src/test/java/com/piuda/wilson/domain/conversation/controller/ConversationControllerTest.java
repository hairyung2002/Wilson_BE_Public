package com.piuda.wilson.domain.conversation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.conversation.dto.ConversationDetailResponse;
import com.piuda.wilson.domain.conversation.dto.ConversationDetailResponse.MessageResponse;
import com.piuda.wilson.domain.conversation.dto.ConversationSummaryResponse;
import com.piuda.wilson.domain.conversation.dto.FinishConversationResponse;
import com.piuda.wilson.domain.conversation.dto.StartConversationResponse;
import com.piuda.wilson.domain.conversation.entity.ConversationOrigin;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import com.piuda.wilson.domain.conversation.service.ConversationService;
import com.piuda.wilson.domain.conversation.service.ProactiveService;
import com.piuda.wilson.global.common.response.PageResponse;
import com.piuda.wilson.global.config.SecurityConfig;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.global.security.JwtTokenProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ConversationController.class)
@Import(SecurityConfig.class)
class ConversationControllerTest {

    private static final Long MEMBER_ID = 5L;
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 10, 15, 9, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private ProactiveService proactiveService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    private RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of()));
    }

    @Test
    @DisplayName("대화를 시작하면 201과 WAITING 상태를 반환한다")
    void start_success() throws Exception {
        // given
        given(conversationService.start(eq(MEMBER_ID), any()))
                .willReturn(new StartConversationResponse(15L, ConversationState.WAITING));

        // when & then
        mockMvc.perform(post("/api/v1/conversations")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationType":"DAILY"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversationId").value(15))
                .andExpect(jsonPath("$.data.state").value("WAITING"));
    }

    @Test
    @DisplayName("푸시로 받은 트리거로 선제 대화를 시작하면 201")
    void startProactive() throws Exception {
        // given
        given(proactiveService.start(MEMBER_ID, "8f1c-trigger"))
                .willReturn(new StartConversationResponse(17L, ConversationState.WAITING));

        // when & then
        mockMvc.perform(post("/api/v1/conversations/proactive")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"triggerId":"8f1c-trigger"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversationId").value(17));
        mockMvc.perform(post("/api/v1/conversations/proactive")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("대화 유형이 없으면 400을 반환한다")
    void start_missingType_badRequest() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/conversations")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("conversationType"));
    }

    @Test
    @DisplayName("정의되지 않은 대화 유형이면 400을 반환한다")
    void start_unknownType_badRequest() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/conversations")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationType":"KARAOKE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    @DisplayName("인증되지 않으면 401을 반환한다")
    void start_unauthorized() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationType":"DAILY"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("대화를 종료하면 200과 FINISHED 상태를 반환한다")
    void finish_success() throws Exception {
        // given
        given(conversationService.finish(MEMBER_ID, 15L)).willReturn(
                new FinishConversationResponse(15L, ConversationState.FINISHED, STARTED_AT.plusMinutes(10)));

        // when & then
        mockMvc.perform(patch("/api/v1/conversations/15/finish").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("FINISHED"))
                .andExpect(jsonPath("$.data.endedAt").value("2026-10-15T09:10:00"));
    }

    @Test
    @DisplayName("이미 종료된 대화를 종료하면 409를 반환한다")
    void finish_alreadyFinished_conflict() throws Exception {
        // given
        given(conversationService.finish(MEMBER_ID, 15L))
                .willThrow(new CustomException(ErrorCode.CONVERSATION_ALREADY_FINISHED));

        // when & then
        mockMvc.perform(patch("/api/v1/conversations/15/finish").with(loginMember()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CV002"));
    }

    @Test
    @DisplayName("대화 목록을 명세의 페이지 형식으로 반환한다")
    void getConversations_success() throws Exception {
        // given
        given(conversationService.getConversations(MEMBER_ID, 1L, LocalDate.of(2026, 10, 15), PageRequest.of(0, 20)))
                .willReturn(new PageResponse<>(0, 20, 1, 1, List.of(new ConversationSummaryResponse(
                        15L, ConversationType.DAILY, ConversationOrigin.USER, ConversationState.FINISHED, STARTED_AT, null, null))));

        // when & then
        mockMvc.perform(get("/api/v1/conversations")
                        .with(loginMember())
                        .param("elderlyId", "1")
                        .param("date", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].conversationId").value(15))
                .andExpect(jsonPath("$.data.content[0].startedAt").value("2026-10-15T09:00:00"));
    }

    @Test
    @DisplayName("노인 식별자 없이 목록을 조회하면 400을 반환한다")
    void getConversations_missingElderlyId_badRequest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/conversations").with(loginMember()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    @DisplayName("날짜 형식이 잘못되면 400을 반환한다")
    void getConversations_malformedDate_badRequest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/conversations")
                        .with(loginMember())
                        .param("elderlyId", "1")
                        .param("date", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    @DisplayName("페이지 크기가 100을 넘으면 400을 반환한다")
    void getConversations_sizeTooLarge_badRequest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/conversations")
                        .with(loginMember())
                        .param("elderlyId", "1")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
        verify(conversationService, never()).getConversations(any(), any(), any(), any());
    }

    @Test
    @DisplayName("대화 상세와 메시지를 반환한다")
    void getConversation_success() throws Exception {
        // given
        given(conversationService.getConversation(MEMBER_ID, 15L)).willReturn(new ConversationDetailResponse(
                15L, ConversationType.DAILY, ConversationOrigin.USER, ConversationState.FINISHED, STARTED_AT,
                STARTED_AT.plusMinutes(10),
                null, true,
                List.of(new MessageResponse(MessageSender.AI, "식사는 하셨어요?", STARTED_AT),
                        new MessageResponse(MessageSender.ELDERLY, "응 먹었어", STARTED_AT.plusSeconds(5)))));

        // when & then
        mockMvc.perform(get("/api/v1/conversations/15").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transcriptVisible").value(true))
                .andExpect(jsonPath("$.data.summary").isEmpty())
                .andExpect(jsonPath("$.data.messages[0].role").value("AI"))
                .andExpect(jsonPath("$.data.messages[1].text").value("응 먹었어"));
    }

    @Test
    @DisplayName("연결되지 않은 보호자가 상세를 조회하면 403을 반환한다")
    void getConversation_noPermission_forbidden() throws Exception {
        // given
        given(conversationService.getConversation(MEMBER_ID, 15L))
                .willThrow(new CustomException(ErrorCode.NO_PERMISSION));

        // when & then
        mockMvc.perform(get("/api/v1/conversations/15").with(loginMember()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("대화를 삭제하면 204를 반환한다")
    void delete_success() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/conversations/15").with(loginMember()))
                .andExpect(status().isNoContent());
        verify(conversationService).delete(MEMBER_ID, 15L);
    }
}
