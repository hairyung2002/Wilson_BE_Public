package com.piuda.wilson.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String TOKEN = "access-token";
    private static final long ISSUED_AT = 1_700_000_000_123L;

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final TokenRevocationRepository tokenRevocationRepository = mock(TokenRevocationRepository.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtTokenProvider, tokenRevocationRepository);
    private final MockFilterChain chain = new MockFilterChain();

    @BeforeEach
    void setUp() {
        given(jwtTokenProvider.validateToken(TOKEN)).willReturn(true);
        given(jwtTokenProvider.getMemberId(TOKEN)).willReturn(5L);
        given(jwtTokenProvider.getIssuedAtEpochMilli(TOKEN)).willReturn(ISSUED_AT);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Authentication filterWithToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("유효하고 무효화되지 않은 토큰이면 회원 ID로 인증한다")
    void authenticate() throws Exception {
        // given
        given(tokenRevocationRepository.findRevokedAtEpochMilli(5L)).willReturn(Optional.empty());

        // when
        Authentication authentication = filterWithToken();

        // then
        assertThat(authentication.getPrincipal()).isEqualTo(5L);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("로그아웃·탈퇴 이전에 발급된 토큰은 인증하지 않는다")
    void revokedToken() throws Exception {
        // given
        given(tokenRevocationRepository.findRevokedAtEpochMilli(5L)).willReturn(Optional.of(ISSUED_AT + 1));

        // when
        Authentication authentication = filterWithToken();

        // then
        assertThat(authentication).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("무효화 시각 이후에 새로 발급된 토큰은 인증한다")
    void tokenIssuedAfterRevocation() throws Exception {
        // given
        given(tokenRevocationRepository.findRevokedAtEpochMilli(5L)).willReturn(Optional.of(ISSUED_AT));

        // when & then
        assertThat(filterWithToken().getPrincipal()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Redis 장애로 무효화 여부를 확인할 수 없으면 서비스 중단을 막기 위해 인증을 유지한다")
    void redisFailure_failOpen() throws Exception {
        // given
        given(tokenRevocationRepository.findRevokedAtEpochMilli(5L))
                .willThrow(new RedisConnectionFailureException("redis down"));

        // when & then
        assertThat(filterWithToken().getPrincipal()).isEqualTo(5L);
    }

    @Test
    @DisplayName("토큰이 없으면 무효화 여부를 조회하지 않는다")
    void noToken() throws Exception {
        // when
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenRevocationRepository, never()).findRevokedAtEpochMilli(5L);
    }
}
