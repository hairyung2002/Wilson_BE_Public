package com.piuda.wilson.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.auth.service.AuthService;
import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.member.dto.ChangePasswordRequest;
import com.piuda.wilson.domain.member.dto.MemberResponse;
import com.piuda.wilson.domain.member.dto.UpdateMemberRequest;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.member.repository.MemberRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CAREGIVER_ID = 3L;
    private static final Long ELDERLY_ID = 15L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CaregiverRepository caregiverRepository;

    @Mock
    private CareRelationshipRepository careRelationshipRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthService authService;

    @Mock
    private ElderlyRepository elderlyRepository;

    @InjectMocks
    private UserService userService;

    private Member caregiverMember() {
        Member member = Member.builder().email("care@wilson.com").password("encoded").name("보호자")
                .phone("09012345678").memberType(MemberType.CAREGIVER).build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    private Member elderlyMember() {
        Member member = Member.builder().name("김철수").memberType(MemberType.ELDERLY).build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    private void givenMember(Member member) {
        given(memberRepository.findByIdAndDeletedFalse(MEMBER_ID)).willReturn(Optional.of(member));
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    // ===== 4-1 내 정보 조회 =====

    @Test
    @DisplayName("내 정보를 조회한다")
    void getMe() {
        // given
        givenMember(caregiverMember());

        // when
        MemberResponse response = userService.getMe(MEMBER_ID);

        // then
        assertThat(response.id()).isEqualTo(MEMBER_ID);
        assertThat(response.email()).isEqualTo("care@wilson.com");
        assertThat(response.phone()).isEqualTo("09012345678");
        assertThat(response.memberType()).isEqualTo(MemberType.CAREGIVER);
        assertThat(response.elderlyId()).isNull();
    }

    @Test
    @DisplayName("노인 회원도 내 정보를 조회할 수 있고, 일정·설정 API에 쓸 노인 식별자를 함께 받는다")
    void getMe_elderly() {
        // given
        givenMember(elderlyMember());
        Elderly elderly = Elderly.builder().name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", ELDERLY_ID);
        given(elderlyRepository.findByMemberIdAndDeletedFalse(MEMBER_ID)).willReturn(Optional.of(elderly));

        // when
        MemberResponse response = userService.getMe(MEMBER_ID);

        // then
        assertThat(response.name()).isEqualTo("김철수");
        assertThat(response.elderlyId()).isEqualTo(ELDERLY_ID);
    }

    @Test
    @DisplayName("탈퇴했거나 없는 회원이면 예외를 던진다")
    void getMe_notFound() {
        // given
        given(memberRepository.findByIdAndDeletedFalse(MEMBER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getMe(MEMBER_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.MEMBER_NOT_FOUND));
    }

    // ===== 4-2 회원정보 수정 =====

    @Test
    @DisplayName("요청한 항목만 수정하고 이메일은 바뀌지 않는다")
    void updateMe() {
        // given
        Member member = caregiverMember();
        givenMember(member);

        // when
        MemberResponse response = userService.updateMe(MEMBER_ID, new UpdateMemberRequest("홍길동", null));

        // then
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.phone()).isEqualTo("09012345678");
        assertThat(response.email()).isEqualTo("care@wilson.com");
    }

    @Test
    @DisplayName("노인 회원은 회원정보를 수정할 수 없다")
    void updateMe_elderly_forbidden() {
        // given
        Member member = elderlyMember();
        givenMember(member);

        // when & then
        assertThatThrownBy(() -> userService.updateMe(MEMBER_ID, new UpdateMemberRequest("이름", null)))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
        assertThat(member.getName()).isEqualTo("김철수");
    }

    // ===== 4-3 비밀번호 변경 =====

    @Test
    @DisplayName("현재 비밀번호가 맞으면 새 비밀번호로 변경한다")
    void changePassword() {
        // given
        Member member = caregiverMember();
        givenMember(member);
        given(passwordEncoder.matches("current-pw", "encoded")).willReturn(true);
        given(passwordEncoder.matches("new-password", "encoded")).willReturn(false);
        given(passwordEncoder.encode("new-password")).willReturn("new-encoded");

        // when
        userService.changePassword(MEMBER_ID, new ChangePasswordRequest("current-pw", "new-password"));

        // then
        assertThat(member.getPassword()).isEqualTo("new-encoded");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 변경하지 않는다")
    void changePassword_mismatch() {
        // given
        Member member = caregiverMember();
        givenMember(member);
        given(passwordEncoder.matches("wrong-pw", "encoded")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(MEMBER_ID,
                new ChangePasswordRequest("wrong-pw", "new-password")))
                .satisfies(e -> assertErrorCode(e, ErrorCode.PASSWORD_MISMATCH));
        assertThat(member.getPassword()).isEqualTo("encoded");
    }

    @Test
    @DisplayName("새 비밀번호가 현재와 같으면 변경하지 않는다")
    void changePassword_same() {
        // given
        givenMember(caregiverMember());
        given(passwordEncoder.matches("same-password", "encoded")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(MEMBER_ID,
                new ChangePasswordRequest("same-password", "same-password")))
                .satisfies(e -> assertErrorCode(e, ErrorCode.SAME_PASSWORD));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("노인 회원은 비밀번호를 변경할 수 없다")
    void changePassword_elderly_forbidden() {
        // given
        givenMember(elderlyMember());

        // when & then
        assertThatThrownBy(() -> userService.changePassword(MEMBER_ID,
                new ChangePasswordRequest("current-pw", "new-password")))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    // ===== 4-4 회원 탈퇴 =====

    @Test
    @DisplayName("탈퇴하면 개인정보를 파기하고, 노인과의 연결을 끊고, 모든 기기에서 로그아웃한다")
    void withdraw() {
        // given
        Member member = caregiverMember();
        givenMember(member);
        Caregiver caregiver = Caregiver.builder().member(member).build();
        ReflectionTestUtils.setField(caregiver, "id", CAREGIVER_ID);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver));

        // when
        userService.withdraw(MEMBER_ID);

        // then
        assertThat(member.isDeleted()).isTrue();
        assertThat(member.getEmail()).isNull();
        assertThat(member.getPhone()).isNull();
        assertThat(member.getPassword()).isNull();
        assertThat(member.getName()).isEqualTo("탈퇴한 회원");
        verify(careRelationshipRepository).deleteByCaregiverId(CAREGIVER_ID);
        verify(authService).logout(MEMBER_ID);
    }

    private Elderly givenCaregiverWithRelationship(Member member, CareRole role) {
        Caregiver caregiver = Caregiver.builder().member(member).build();
        ReflectionTestUtils.setField(caregiver, "id", CAREGIVER_ID);
        Elderly elderly = Elderly.builder().name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", ELDERLY_ID);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver));
        given(careRelationshipRepository.findByCaregiverId(CAREGIVER_ID)).willReturn(List.of(
                CareRelationship.builder().caregiver(caregiver).elderly(elderly).role(role).build()));
        return elderly;
    }

    @Test
    @DisplayName("다른 보호자가 있는 노인의 주보호자는 주보호자를 넘기기 전에는 탈퇴할 수 없다")
    void withdraw_ownerWithOtherCaregivers() {
        // given
        Member member = caregiverMember();
        givenMember(member);
        givenCaregiverWithRelationship(member, CareRole.OWNER);
        given(careRelationshipRepository.countByElderlyId(ELDERLY_ID)).willReturn(2L);

        // when & then
        assertThatThrownBy(() -> userService.withdraw(MEMBER_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.OWNER_TRANSFER_REQUIRED));
        assertThat(member.isDeleted()).isFalse();
        verify(careRelationshipRepository, never()).deleteByCaregiverId(anyLong());
    }

    @Test
    @DisplayName("다른 보호자가 없는 노인의 주보호자는 탈퇴할 수 있고, 노인은 보호자 없는 상태로 표시된다 (30일 유예 후 파기)")
    void withdraw_soleOwner() {
        // given
        Member member = caregiverMember();
        givenMember(member);
        Elderly elderly = givenCaregiverWithRelationship(member, CareRole.OWNER);
        given(careRelationshipRepository.countByElderlyId(ELDERLY_ID)).willReturn(1L);

        // when
        userService.withdraw(MEMBER_ID);

        // then
        assertThat(member.isDeleted()).isTrue();
        assertThat(elderly.getOrphanedAt()).isNotNull();
        verify(careRelationshipRepository).deleteByCaregiverId(CAREGIVER_ID);
    }

    @Test
    @DisplayName("MEMBER로만 연결된 보호자는 다른 보호자가 있어도 탈퇴할 수 있다")
    void withdraw_member() {
        // given
        Member member = caregiverMember();
        givenMember(member);
        Elderly elderly = givenCaregiverWithRelationship(member, CareRole.MEMBER);
        given(careRelationshipRepository.countByElderlyId(ELDERLY_ID)).willReturn(2L);

        // when
        userService.withdraw(MEMBER_ID);

        // then
        assertThat(member.isDeleted()).isTrue();
        assertThat(elderly.getOrphanedAt()).as("다른 보호자가 남아 있으면 보호자 없는 상태가 아니다").isNull();
    }

    @Test
    @DisplayName("노인 회원은 탈퇴할 수 없다")
    void withdraw_elderly_forbidden() {
        // given
        Member member = elderlyMember();
        givenMember(member);

        // when & then
        assertThatThrownBy(() -> userService.withdraw(MEMBER_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
        assertThat(member.isDeleted()).isFalse();
        verify(careRelationshipRepository, never()).deleteByCaregiverId(anyLong());
        verify(authService, never()).logout(anyLong());
    }
}
