package com.piuda.wilson.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.member.repository.MemberRepository;
import com.piuda.wilson.domain.notification.dto.RegisterPushTokenRequest;
import com.piuda.wilson.domain.notification.entity.PushPlatform;
import com.piuda.wilson.domain.notification.entity.PushToken;
import com.piuda.wilson.domain.notification.repository.PushTokenRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final RegisterPushTokenRequest REQUEST = new RegisterPushTokenRequest("fcm-new", PushPlatform.IOS);

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private PushTokenService pushTokenService;

    private static Member member(Long id) {
        Member member = Member.builder().name("회원").memberType(MemberType.CAREGIVER).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("처음 등록하면 회원의 푸시 토큰을 저장한다")
    void register_new() {
        // given
        given(pushTokenRepository.findByToken("fcm-new")).willReturn(Optional.empty());
        given(pushTokenRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());
        given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member(MEMBER_ID));

        // when
        pushTokenService.register(MEMBER_ID, REQUEST);

        // then
        ArgumentCaptor<PushToken> captor = ArgumentCaptor.forClass(PushToken.class);
        verify(pushTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("fcm-new");
        assertThat(captor.getValue().getPlatform()).isEqualTo(PushPlatform.IOS);
    }

    @Test
    @DisplayName("이미 토큰이 있으면 마지막 기기 토큰으로 바꾼다 (회원당 1개)")
    void register_replace() {
        // given
        PushToken existing = PushToken.builder().member(member(MEMBER_ID)).token("fcm-old")
                .platform(PushPlatform.ANDROID).build();
        given(pushTokenRepository.findByToken("fcm-new")).willReturn(Optional.empty());
        given(pushTokenRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(existing));

        // when
        pushTokenService.register(MEMBER_ID, REQUEST);

        // then
        assertThat(existing.getToken()).isEqualTo("fcm-new");
        assertThat(existing.getPlatform()).isEqualTo(PushPlatform.IOS);
        verify(pushTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 기기 토큰이 다른 회원에게 있으면 지운다 (기기에서 계정을 바꾼 경우 남의 알림이 오지 않도록)")
    void register_takeOverFromOtherMember() {
        // given
        PushToken others = PushToken.builder().member(member(2L)).token("fcm-new").platform(PushPlatform.IOS).build();
        given(pushTokenRepository.findByToken("fcm-new")).willReturn(Optional.of(others));
        given(pushTokenRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());
        given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member(MEMBER_ID));

        // when
        pushTokenService.register(MEMBER_ID, REQUEST);

        // then
        verify(pushTokenRepository).delete(others);
        verify(pushTokenRepository).flush();
        verify(pushTokenRepository).save(any(PushToken.class));
    }

    @Test
    @DisplayName("로그아웃·탈퇴 시 회원의 푸시 토큰을 지운다")
    void unregister() {
        // when
        pushTokenService.unregister(MEMBER_ID);

        // then
        verify(pushTokenRepository).deleteByMemberId(MEMBER_ID);
    }
}
