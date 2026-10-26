package com.piuda.wilson.global.security;

import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRevocationRepository tokenRevocationRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Long memberId = jwtTokenProvider.getMemberId(token);
            if (!isRevoked(token, memberId)) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(memberId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 로그아웃·탈퇴 이전에 발급된 토큰인지 확인한다.
     *
     * <p>Redis 장애 시에는 노인 대화 등 서비스 전체가 멈추지 않도록 유효한 것으로 본다.
     * 액세스 토큰 수명(최대 1시간)이 지나면 어차피 만료된다.</p>
     */
    private boolean isRevoked(String token, Long memberId) {
        try {
            return tokenRevocationRepository.findRevokedAtEpochMilli(memberId)
                    .map(revokedAt -> jwtTokenProvider.getIssuedAtEpochMilli(token) < revokedAt)
                    .orElse(false);
        } catch (DataAccessException e) {
            log.error("Token revocation check unavailable, allowing request: memberId={}", memberId, e);
            return false;
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
