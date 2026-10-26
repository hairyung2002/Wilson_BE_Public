package com.piuda.wilson.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("CustomException을 ErrorCode에 맞는 상태와 표준 에러 응답으로 변환한다")
    void handleCustomException() throws Exception {
        // when & then
        mockMvc.perform(get("/test/custom"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C004"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("예상치 못한 예외를 500 표준 에러 응답으로 변환한다")
    void handleUnexpectedException() throws Exception {
        // when & then
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("읽을 수 없는 요청 본문을 400 표준 에러 응답으로 변환한다")
    void handleNotReadableBody() throws Exception {
        // when & then
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터 누락을 400 표준 에러 응답으로 변환한다")
    void handleMissingParameter() throws Exception {
        // when & then
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    @DisplayName("파라미터 타입 불일치를 400 표준 에러 응답으로 변환한다")
    void handleTypeMismatch() throws Exception {
        // when & then
        mockMvc.perform(get("/test/param").param("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/param")
        void param(@RequestParam Long id) {
        }

        @PostMapping("/test/body")
        void body(@RequestBody Map<String, String> body) {
        }

        @GetMapping("/test/custom")
        void custom() {
            throw new CustomException(ErrorCode.ENTITY_NOT_FOUND);
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("unexpected");
        }
    }
}
