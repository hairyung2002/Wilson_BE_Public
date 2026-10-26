package com.piuda.wilson.domain.elderly.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
import com.piuda.wilson.domain.elderly.dto.CreateElderlyResponse;
import com.piuda.wilson.domain.elderly.dto.DeviceCodeResponse;
import com.piuda.wilson.domain.elderly.dto.ElderlyDetailResponse;
import com.piuda.wilson.domain.elderly.dto.ElderlySummaryResponse;
import com.piuda.wilson.domain.elderly.entity.Gender;
import com.piuda.wilson.domain.elderly.service.ElderlyService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ElderlyController.class)
@Import(SecurityConfig.class)
class ElderlyControllerTest {

    private static final Long MEMBER_ID = 5L;
    private static final LocalDate BIRTH = LocalDate.of(1945, 3, 11);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ElderlyService elderlyService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    private RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of()));
    }

    @Test
    @DisplayName("노인을 등록하면 201과 노인 식별자를 반환한다")
    void register_success() throws Exception {
        // given
        String requestBody = """
                {"name":"김철수","birth":"1945-03-11","gender":"MALE","relationship":"FATHER"}
                """;
        given(elderlyService.register(eq(MEMBER_ID), any())).willReturn(new CreateElderlyResponse(15L));

        // when & then
        mockMvc.perform(post("/api/v1/elderlies")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.elderlyId").value(15));
    }

    @Test
    @DisplayName("이름이 없으면 400을 반환한다")
    void register_blankName_badRequest() throws Exception {
        // given
        String requestBody = """
                {"name":" ","birth":"1945-03-11","gender":"MALE","relationship":"FATHER"}
                """;

        // when & then
        mockMvc.perform(post("/api/v1/elderlies")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        verify(elderlyService, never()).register(any(), any());
    }

    @Test
    @DisplayName("생년월일이 미래면 400을 반환한다")
    void register_futureBirth_badRequest() throws Exception {
        // given
        String requestBody = """
                {"name":"김철수","birth":"%s","gender":"MALE","relationship":"FATHER"}
                """.formatted(LocalDate.now().plusDays(1));

        // when & then
        mockMvc.perform(post("/api/v1/elderlies")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("birth"));
    }

    @Test
    @DisplayName("생년월일 형식이 잘못되면 400을 반환한다")
    void register_malformedBirth_badRequest() throws Exception {
        // given
        String requestBody = """
                {"name":"김철수","birth":"1945-13-45","gender":"MALE","relationship":"FATHER"}
                """;

        // when & then
        mockMvc.perform(post("/api/v1/elderlies")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    @DisplayName("인증되지 않으면 401을 반환한다")
    void register_unauthorized() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/elderlies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김철수","birth":"1945-03-11","gender":"MALE","relationship":"FATHER"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("연결된 노인 목록을 반환한다")
    void getElderlies_success() throws Exception {
        // given
        given(elderlyService.getElderlies(MEMBER_ID))
                .willReturn(List.of(new ElderlySummaryResponse(1L, "김철수", 81, "FATHER")));

        // when & then
        mockMvc.perform(get("/api/v1/elderlies").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].age").value(81))
                .andExpect(jsonPath("$.data[0].relationship").value("FATHER"));
    }

    @Test
    @DisplayName("노인 상세 정보를 반환한다")
    void getElderly_success() throws Exception {
        // given
        given(elderlyService.getElderly(MEMBER_ID, 1L))
                .willReturn(new ElderlyDetailResponse(1L, "김철수", BIRTH, Gender.MALE, "FATHER"));

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/1").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김철수"))
                .andExpect(jsonPath("$.data.birth").value("1945-03-11"))
                .andExpect(jsonPath("$.data.gender").value("MALE"));
    }

    @Test
    @DisplayName("연결되지 않은 노인을 조회하면 403을 반환한다")
    void getElderly_noPermission_forbidden() throws Exception {
        // given
        given(elderlyService.getElderly(MEMBER_ID, 1L)).willThrow(new CustomException(ErrorCode.NO_PERMISSION));

        // when & then
        mockMvc.perform(get("/api/v1/elderlies/1").with(loginMember()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C005"));
    }

    @Test
    @DisplayName("노인 정보를 수정하면 수정된 상세 정보를 반환한다")
    void update_success() throws Exception {
        // given
        given(elderlyService.update(eq(MEMBER_ID), eq(1L), any()))
                .willReturn(new ElderlyDetailResponse(1L, "김철순", BIRTH, Gender.MALE, "GRANDFATHER"));

        // when & then
        mockMvc.perform(patch("/api/v1/elderlies/1")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김철순","relationship":"GRANDFATHER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김철순"))
                .andExpect(jsonPath("$.data.relationship").value("GRANDFATHER"));
    }

    @Test
    @DisplayName("수정 요청의 이름이 공백이면 400을 반환한다")
    void update_blankName_badRequest() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/elderlies/1")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("노인을 삭제하면 204를 반환한다")
    void delete_success() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/elderlies/1").with(loginMember()))
                .andExpect(status().isNoContent());
        verify(elderlyService).delete(MEMBER_ID, 1L);
    }

    @Test
    @DisplayName("연결되지 않은 노인을 삭제하면 403을 반환한다")
    void delete_noPermission_forbidden() throws Exception {
        // given
        willThrow(new CustomException(ErrorCode.NO_PERMISSION)).given(elderlyService).delete(MEMBER_ID, 1L);

        // when & then
        mockMvc.perform(delete("/api/v1/elderlies/1").with(loginMember()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("기기 연결 코드를 발급하면 201과 코드, 만료 시각을 반환한다")
    void issueDeviceCode_success() throws Exception {
        // given
        given(elderlyService.issueDeviceCode(MEMBER_ID, 1L))
                .willReturn(new DeviceCodeResponse("012345", LocalDateTime.of(2026, 10, 15, 21, 40)));

        // when & then
        mockMvc.perform(post("/api/v1/elderlies/1/device-code").with(loginMember()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deviceCode").value("012345"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-10-15T21:40:00"));
    }
}
