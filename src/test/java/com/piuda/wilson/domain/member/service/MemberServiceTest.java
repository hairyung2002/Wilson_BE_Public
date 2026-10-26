package com.piuda.wilson.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.elderly.dto.ConsentItem;
import com.piuda.wilson.domain.elderly.entity.ConsentGrantor;
import com.piuda.wilson.domain.elderly.entity.ConsentType;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.entity.LegalBasis;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.elderly.service.ConsentService;
import java.util.List;
import com.piuda.wilson.domain.member.dto.CreateMemberRequest;
import com.piuda.wilson.domain.member.dto.CreateMemberResponse;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.member.repository.MemberRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
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
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private CaregiverRepository caregiverRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ConsentService consentService;

    @InjectMocks
    private MemberService memberService;

    private static final List<ConsentItem> CONSENTS = List.of(new ConsentItem(ConsentType.SENSITIVE_INFO, true, 1));

    private Member savedMember(String email, MemberType type) {
        Member member = Member.builder()
                .email(email).password("encoded-password").name("김윌슨").memberType(type).build();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    @Test
    @DisplayName("노인 회원가입 시 비밀번호를 암호화하고 노인 프로필을 생성한다")
    void create_elderly_success() {
        // given
        CreateMemberRequest request = new CreateMemberRequest("elderly@wilson.com", "raw-password", "김윌슨",
                MemberType.ELDERLY, CONSENTS);
        given(memberRepository.existsByEmail("elderly@wilson.com")).willReturn(false);
        given(passwordEncoder.encode("raw-password")).willReturn("encoded-password");
        given(memberRepository.save(any(Member.class)))
                .willReturn(savedMember("elderly@wilson.com", MemberType.ELDERLY));

        // when
        CreateMemberResponse response = memberService.create(request);

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.memberType()).isEqualTo(MemberType.ELDERLY);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-password");
        verify(elderlyRepository).save(any(Elderly.class));
        verify(caregiverRepository, never()).save(any());
        verify(consentService).recordInitial(any(), eq(CONSENTS), eq(1L), eq(ConsentGrantor.SELF), eq(LegalBasis.SELF));
    }

    @Test
    @DisplayName("보호자 회원가입 시 보호자 프로필을 생성한다")
    void create_caregiver_success() {
        // given
        CreateMemberRequest request = new CreateMemberRequest("caregiver@wilson.com", "raw-password", "김윌슨",
                MemberType.CAREGIVER, null);
        given(memberRepository.existsByEmail("caregiver@wilson.com")).willReturn(false);
        given(passwordEncoder.encode("raw-password")).willReturn("encoded-password");
        given(memberRepository.save(any(Member.class)))
                .willReturn(savedMember("caregiver@wilson.com", MemberType.CAREGIVER));

        // when
        memberService.create(request);

        // then
        verify(caregiverRepository).save(any(Caregiver.class));
        verify(elderlyRepository, never()).save(any());
        verify(consentService, never()).recordInitial(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("이미 사용 중인 이메일이면 예외를 던지고 저장하지 않는다")
    void create_duplicateEmail_throws() {
        // given
        CreateMemberRequest request = new CreateMemberRequest("dup@wilson.com", "raw-password", "홍길동",
                MemberType.CAREGIVER, null);
        given(memberRepository.existsByEmail("dup@wilson.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.create(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_EMAIL));
        verify(memberRepository, never()).save(any());
    }
}
