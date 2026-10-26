package com.piuda.wilson.global.security;

import com.piuda.wilson.domain.member.entity.MemberType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    /** 표준 iat는 초 단위라 로그아웃 직후 재발급된 토큰과 구분하지 못한다. 그래서 밀리초 발급 시각을 따로 담는다. */
    private static final String ISSUED_AT_MILLIS = "iat_ms";

    private final SecretKey key;
    private final long expirationMillis;
    private final long refreshExpirationMillis;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = jwtProperties.expirationMillis();
        this.refreshExpirationMillis = jwtProperties.refreshExpirationMillis();
    }

    public String createToken(Long memberId, MemberType memberType) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("type", memberType.name())
                .claim(ISSUED_AT_MILLIS, now.getTime())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(Long memberId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMillis);
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public long refreshExpirationMillis() {
        return refreshExpirationMillis;
    }

    public long accessExpirationMillis() {
        return expirationMillis;
    }

    /** 밀리초 발급 시각이 없는 토큰(이 클레임 도입 전 발급분)은 표준 iat로 대신한다. */
    public long getIssuedAtEpochMilli(String token) {
        Claims claims = parseClaims(token);
        Object issuedAtMillis = claims.get(ISSUED_AT_MILLIS);
        if (issuedAtMillis instanceof Number number) {
            return number.longValue();
        }
        return claims.getIssuedAt().getTime();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getMemberId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
