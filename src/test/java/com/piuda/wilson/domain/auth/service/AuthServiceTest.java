package com.piuda.wilson.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private DeviceCodeRepository deviceCodeRepository;

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private TokenRevocationRepository tokenRevocationRepository;

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @Mock
    private PushTokenService pushTokenService;

    @InjectMocks
    private AuthService authService;

    private static final String CLIENT_IP = "1.2.3.4";

    @Test
    @DisplayName("로그아웃하면 리프레시·푸시 토큰을 지우고, 지금까지 발급된 액세스 토큰을 모두 무효화한다")
    void logout() {
        // given
        given(jwtTokenProvider.accessExpirationMillis()).willReturn(3_600_000L);
        long before = Instant.now().toEpochMilli();

        // when
        authService.logout(5L);

        // then
        verify(refreshTokenRepository).deleteByMemberId(5L);
        verify(pushTokenService).unregister(5L);
        ArgumentCaptor<Long> revokedAt = ArgumentCaptor.forClass(Long.class);
        verify(tokenRevocationRepository).revokeAllIssuedBefore(eq(5L), revokedAt.capture(), eq(Duration.ofHours(1)));
        assertThat(revokedAt.getValue()).as("로그아웃 시점(밀리초) 기준으로 무효화")
                .isBetween(before + 1, Instant.now().toEpochMilli() + 1);
    }

    @Test
    @DisplayName("같은 IP에서 10분에 10회를 넘게 기기 로그인을 시도하면 코드를 확인하지 않고 거절한다")
    void deviceLogin_tooManyAttempts() {
        // given
        given(loginAttemptRepository.incrementDeviceLogin(CLIENT_IP, Duration.ofMinutes(10))).willReturn(11L);

        // when & then
        assertThatThrownBy(() -> authService.deviceLogin(new DeviceLoginRequest("123456"), CLIENT_IP))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TOO_MANY_DEVICE_LOGIN_ATTEMPTS));
        verify(deviceCodeRepository, never()).consume(any());
    }

    @Test
    @DisplayName("10번째 시도까지는 코드를 확인한다")
    void deviceLogin_tenthAttemptAllowed() {
        // given
        given(loginAttemptRepository.incrementDeviceLogin(CLIENT_IP, Duration.ofMinutes(10))).willReturn(10L);
        given(deviceCodeRepository.consume("000000")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.deviceLogin(new DeviceLoginRequest("000000"), CLIENT_IP))
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_DEVICE_CODE));
    }

    private Member memberWithId(Long id) {
        Member member = Member.builder()
                .email("user@wilson.com").password("encoded").name("사용자").memberType(MemberType.CAREGIVER)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("로그인 성공 시 액세스/리프레시 토큰을 발급하고 리프레시를 저장한다")
    void login_success() {
        // given
        LoginRequest request = new LoginRequest("user@wilson.com", "raw-password");
        given(memberRepository.findByEmail("user@wilson.com")).willReturn(Optional.of(memberWithId(7L)));
        given(passwordEncoder.matches("raw-password", "encoded")).willReturn(true);
        given(jwtTokenProvider.createToken(7L, MemberType.CAREGIVER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(7L)).willReturn("refresh-token");

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(refreshTokenRepository).save(eq(7L), eq("refresh-token"), anyLong());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 예외를 던진다")
    void login_wrongPassword_throws() {
        // given
        LoginRequest request = new LoginRequest("user@wilson.com", "wrong-password");
        given(memberRepository.findByEmail("user@wilson.com")).willReturn(Optional.of(memberWithId(7L)));
        given(passwordEncoder.matches("wrong-password", "encoded")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        verify(refreshTokenRepository, never()).save(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 예외를 던진다")
    void login_unknownEmail_throws() {
        // given
        LoginRequest request = new LoginRequest("unknown@wilson.com", "raw-password");
        given(memberRepository.findByEmail("unknown@wilson.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("유효한 리프레시 토큰으로 액세스/리프레시 토큰을 회전 발급한다")
    void reissue_success() {
        // given
        ReissueRequest request = new ReissueRequest("valid-refresh");
        given(jwtTokenProvider.validateToken("valid-refresh")).willReturn(true);
        given(jwtTokenProvider.getMemberId("valid-refresh")).willReturn(7L);
        given(refreshTokenRepository.findByMemberId(7L)).willReturn(Optional.of("valid-refresh"));
        given(memberRepository.findById(7L)).willReturn(Optional.of(memberWithId(7L)));
        given(jwtTokenProvider.createToken(7L, MemberType.CAREGIVER)).willReturn("new-access");
        given(jwtTokenProvider.createRefreshToken(7L)).willReturn("new-refresh");

        // when
        ReissueResponse response = authService.reissue(request);

        // then
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenRepository).save(eq(7L), eq("new-refresh"), anyLong());
    }

    @Test
    @DisplayName("검증에 실패한 리프레시 토큰이면 예외를 던진다")
    void reissue_invalidToken_throws() {
        // given
        ReissueRequest request = new ReissueRequest("invalid-refresh");
        given(jwtTokenProvider.validateToken("invalid-refresh")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
        verify(refreshTokenRepository, never()).findByMemberId(anyLong());
    }

    @Test
    @DisplayName("저장된 리프레시 토큰과 일치하지 않으면 예외를 던진다")
    void reissue_storedMismatch_throws() {
        // given
        ReissueRequest request = new ReissueRequest("valid-refresh");
        given(jwtTokenProvider.validateToken("valid-refresh")).willReturn(true);
        given(jwtTokenProvider.getMemberId("valid-refresh")).willReturn(7L);
        given(refreshTokenRepository.findByMemberId(7L)).willReturn(Optional.of("different-refresh"));

        // when & then
        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
        verify(memberRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("계정 없는 노인이 기기 코드로 로그인하면 노인 회원을 만들어 연결하고 토큰을 발급한다")
    void deviceLogin_createsMember() {
        // given
        Elderly elderly = Elderly.builder().name("김철수").build();
        given(deviceCodeRepository.consume("123456")).willReturn(Optional.of(15L));
        given(elderlyRepository.findByIdAndDeletedFalse(15L)).willReturn(Optional.of(elderly));
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 9L);
            return member;
        });
        given(jwtTokenProvider.createToken(9L, MemberType.ELDERLY)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(9L)).willReturn("refresh-token");

        // when
        LoginResponse response = authService.deviceLogin(new DeviceLoginRequest("123456"), CLIENT_IP);

        // then
        assertThat(response.memberId()).isEqualTo(9L);
        assertThat(response.memberType()).isEqualTo(MemberType.ELDERLY);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(elderly.getMember().getName()).isEqualTo("김철수");
        assertThat(elderly.getMember().getEmail()).isNull();
        verify(refreshTokenRepository).save(eq(9L), eq("refresh-token"), anyLong());
    }

    @Test
    @DisplayName("이미 회원이 연결된 노인은 기존 회원으로 로그인한다")
    void deviceLogin_reusesExistingMember() {
        // given
        Member member = Member.builder().name("김철수").memberType(MemberType.ELDERLY).build();
        ReflectionTestUtils.setField(member, "id", 9L);
        Elderly elderly = Elderly.builder().member(member).name("김철수").build();
        given(deviceCodeRepository.consume("123456")).willReturn(Optional.of(15L));
        given(elderlyRepository.findByIdAndDeletedFalse(15L)).willReturn(Optional.of(elderly));
        given(jwtTokenProvider.createToken(9L, MemberType.ELDERLY)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(9L)).willReturn("refresh-token");

        // when
        LoginResponse response = authService.deviceLogin(new DeviceLoginRequest("123456"), CLIENT_IP);

        // then
        assertThat(response.memberId()).isEqualTo(9L);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("없거나 만료되었거나 이미 사용한 코드면 예외를 던진다")
    void deviceLogin_invalidCode_throws() {
        // given
        given(deviceCodeRepository.consume("000000")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.deviceLogin(new DeviceLoginRequest("000000"), CLIENT_IP))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_DEVICE_CODE));
        verify(elderlyRepository, never()).findByIdAndDeletedFalse(anyLong());
    }

    @Test
    @DisplayName("코드 발급 후 노인이 삭제되었으면 예외를 던진다")
    void deviceLogin_deletedElderly_throws() {
        // given
        given(deviceCodeRepository.consume("123456")).willReturn(Optional.of(15L));
        given(elderlyRepository.findByIdAndDeletedFalse(15L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.deviceLogin(new DeviceLoginRequest("123456"), CLIENT_IP))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_DEVICE_CODE));
        verify(memberRepository, never()).save(any());
    }
}
