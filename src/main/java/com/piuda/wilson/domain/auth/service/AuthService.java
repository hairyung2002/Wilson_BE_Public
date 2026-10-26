package com.piuda.wilson.domain.auth.service;

import com.piuda.wilson.domain.auth.dto.DeviceLoginRequest;
import com.piuda.wilson.domain.auth.dto.LoginRequest;
import com.piuda.wilson.domain.auth.dto.LoginResponse;
import com.piuda.wilson.domain.auth.dto.ReissueRequest;
import com.piuda.wilson.domain.auth.dto.ReissueResponse;
import com.piuda.wilson.domain.auth.repository.DeviceCodeRepository;
import com.piuda.wilson.domain.auth.repository.LoginAttemptRepository;
import com.piuda.wilson.domain.auth.repository.RefreshTokenRepository;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.member.repository.MemberRepository;
import com.piuda.wilson.domain.notification.service.PushTokenService;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.global.security.JwtTokenProvider;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration DEVICE_LOGIN_WINDOW = Duration.ofMinutes(10);
    private static final int DEVICE_LOGIN_MAX_ATTEMPTS = 10;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceCodeRepository deviceCodeRepository;
    private final ElderlyRepository elderlyRepository;
    private final TokenRevocationRepository tokenRevocationRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PushTokenService pushTokenService;

    /**
     * 리프레시·푸시 토큰을 지우고 지금까지 발급된 액세스 토큰을 모두 무효화한다.
     *
     * <p>밀리초 단위로 비교하므로 로그아웃 직후 다시 로그인해 받은 토큰은 유효하다.
     * 로그아웃과 같은 밀리초에 발급된 토큰까지 막기 위해 다음 밀리초부터 유효로 기록한다.</p>
     */
    @Transactional
    public void logout(Long memberId) {
        refreshTokenRepository.deleteByMemberId(memberId);
        pushTokenService.unregister(memberId);
        long revokedAt = Instant.now().toEpochMilli() + 1;
        tokenRevocationRepository.revokeAllIssuedBefore(memberId, revokedAt,
                Duration.ofMillis(jwtTokenProvider.accessExpirationMillis()));
        log.info("Member logged out from all sessions: memberId={}", memberId);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createToken(member.getId(), member.getMemberType());
        String refreshToken = issueRefreshToken(member.getId());
        return LoginResponse.of(accessToken, refreshToken, member.getId(), member.getMemberType());
    }

    @Transactional(readOnly = true)
    public ReissueResponse reissue(ReissueRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);
        String storedToken = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (!storedToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));

        String newAccessToken = jwtTokenProvider.createToken(member.getId(), member.getMemberType());
        String newRefreshToken = issueRefreshToken(member.getId());
        return ReissueResponse.of(newAccessToken, newRefreshToken);
    }

    /** 6자리 코드 무작위 대입을 막기 위해 같은 IP의 시도 횟수를 코드 확인 전에 센다 (성공·실패 모두). */
    @Transactional
    public LoginResponse deviceLogin(DeviceLoginRequest request, String clientIp) {
        long attempts = loginAttemptRepository.incrementDeviceLogin(clientIp, DEVICE_LOGIN_WINDOW);
        if (attempts > DEVICE_LOGIN_MAX_ATTEMPTS) {
            log.warn("Device login rate limited: attempts={}", attempts);
            throw new CustomException(ErrorCode.TOO_MANY_DEVICE_LOGIN_ATTEMPTS);
        }

        Long elderlyId = deviceCodeRepository.consume(request.deviceCode())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_DEVICE_CODE));
        Elderly elderly = elderlyRepository.findByIdAndDeletedFalse(elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_DEVICE_CODE));

        Member member = elderly.getMember() != null ? elderly.getMember() : createDeviceMember(elderly);

        String accessToken = jwtTokenProvider.createToken(member.getId(), member.getMemberType());
        String refreshToken = issueRefreshToken(member.getId());
        log.info("Device login: elderlyId={}, memberId={}", elderly.getId(), member.getId());
        return LoginResponse.of(accessToken, refreshToken, member.getId(), member.getMemberType());
    }

    private Member createDeviceMember(Elderly elderly) {
        Member member = memberRepository.save(Member.builder()
                .name(elderly.getName())
                .memberType(MemberType.ELDERLY)
                .build());
        elderly.linkMember(member);
        return member;
    }

    private String issueRefreshToken(Long memberId) {
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId);
        refreshTokenRepository.save(memberId, refreshToken, jwtTokenProvider.refreshExpirationMillis());
        return refreshToken;
    }
}
