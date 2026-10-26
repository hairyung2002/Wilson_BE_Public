package com.piuda.wilson.integration;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.piuda.wilson.domain.auth.repository.RefreshTokenRepository;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthReissueIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    @Test
    @DisplayName("로그인 후 리프레시 토큰으로 새 액세스 토큰을 재발급받는다")
    void loginThenReissue() throws Exception {
        // given: 가입 후 로그인하여 리프레시 토큰 확보
        signUp("caregiver@wilson.com");
        String refreshToken = JsonPath.read(login("caregiver@wilson.com"), "$.data.refreshToken");
        given(refreshTokenRepository.findByMemberId(anyLong())).willReturn(Optional.of(refreshToken));

        // when & then: 재발급 성공
        String reissueBody = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reissueBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("유효하지 않은 리프레시 토큰이면 401을 반환한다")
    void reissue_invalidToken_unauthorized() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"garbage-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    private void signUp(String email) throws Exception {
        String body = """
                {"email":"%s","password":"raw-password","name":"보호자","memberType":"CAREGIVER"}
                """.formatted(email);
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        String body = """
                {"email":"%s","password":"raw-password"}
                """.formatted(email);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }
}
