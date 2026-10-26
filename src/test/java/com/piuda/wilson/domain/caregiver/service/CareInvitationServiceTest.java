package com.piuda.wilson.domain.caregiver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.auth.repository.LoginAttemptRepository;
import com.piuda.wilson.domain.caregiver.dto.InviteCodeResponse;
import com.piuda.wilson.domain.caregiver.dto.JoinCareRelationshipRequest;
import com.piuda.wilson.domain.caregiver.dto.JoinCareRelationshipResponse;
import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.caregiver.repository.InviteCodeRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.LocalDateTime;
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
class CareInvitationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CAREGIVER_ID = 3L;
    private static final Long ELDERLY_ID = 15L;
    private static final String CODE = "K7Q2M9XD";

    @Mock
    private CaregiverRepository caregiverRepository;

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private CareRelationshipRepository careRelationshipRepository;

    @Mock
    private InviteCodeRepository inviteCodeRepository;

    @Mock
    private InviteCodeGenerator inviteCodeGenerator;

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @InjectMocks
    private CareInvitationService careInvitationService;

    private static Caregiver caregiver() {
        Caregiver caregiver = Caregiver.builder().build();
        ReflectionTestUtils.setField(caregiver, "id", CAREGIVER_ID);
        return caregiver;
    }

    private static Elderly elderly(Member member) {
        Elderly elderly = Elderly.builder().member(member).name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", ELDERLY_ID);
        return elderly;
    }

    private static Member elderlyMember(Long id) {
        Member member = Member.builder().name("김철수").memberType(MemberType.ELDERLY).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private void givenRelationship(CareRole role) {
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver()));
        given(careRelationshipRepository.findByCaregiverIdAndElderlyId(CAREGIVER_ID, ELDERLY_ID))
                .willReturn(Optional.of(CareRelationship.builder().caregiver(caregiver()).elderly(elderly(null))
                        .role(role).build()));
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    // ===== 5-7 초대 코드 발급 =====

    @Test
    @DisplayName("주보호자는 노인의 초대 코드를 발급받는다")
    void issue_byOwner() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(null)));
        givenRelationship(CareRole.OWNER);
        given(inviteCodeGenerator.generate()).willReturn(CODE);
        given(inviteCodeRepository.issue(eq(CODE), eq(ELDERLY_ID), any())).willReturn(true);

        // when
        InviteCodeResponse response = careInvitationService.issueInviteCode(MEMBER_ID, ELDERLY_ID);

        // then
        assertThat(response.inviteCode()).isEqualTo(CODE);
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("일반 보호자(MEMBER)는 초대 코드를 발급할 수 없다")
    void issue_byMember_forbidden() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(null)));
        givenRelationship(CareRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> careInvitationService.issueInviteCode(MEMBER_ID, ELDERLY_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
        verify(inviteCodeRepository, never()).issue(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("연결되지 않은 보호자는 초대 코드를 발급할 수 없다")
    void issue_byStranger_forbidden() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(null)));
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver()));
        given(careRelationshipRepository.findByCaregiverIdAndElderlyId(CAREGIVER_ID, ELDERLY_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> careInvitationService.issueInviteCode(MEMBER_ID, ELDERLY_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("주보호자가 없으면 노인 본인이 첫 보호자를 부를 초대 코드를 발급받는다")
    void issue_byElderlySelf_withoutOwner() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(elderlyMember(MEMBER_ID))));
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());
        given(careRelationshipRepository.existsByElderlyIdAndRole(ELDERLY_ID, CareRole.OWNER)).willReturn(false);
        given(inviteCodeGenerator.generate()).willReturn(CODE);
        given(inviteCodeRepository.issue(eq(CODE), eq(ELDERLY_ID), any())).willReturn(true);

        // when & then
        assertThat(careInvitationService.issueInviteCode(MEMBER_ID, ELDERLY_ID).inviteCode()).isEqualTo(CODE);
    }

    @Test
    @DisplayName("주보호자가 있으면 노인 본인은 초대 코드를 발급할 수 없다 (주보호자가 관리)")
    void issue_byElderlySelf_withOwner_forbidden() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(elderlyMember(MEMBER_ID))));
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());
        given(careRelationshipRepository.existsByElderlyIdAndRole(ELDERLY_ID, CareRole.OWNER)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> careInvitationService.issueInviteCode(MEMBER_ID, ELDERLY_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("다른 노인 회원은 초대 코드를 발급할 수 없다")
    void issue_byOtherElderly_forbidden() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(elderlyMember(99L))));
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> careInvitationService.issueInviteCode(MEMBER_ID, ELDERLY_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("코드가 계속 겹쳐 발급하지 못하면 503을 던진다")
    void issue_collisionExhausted() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(null)));
        givenRelationship(CareRole.OWNER);
        given(inviteCodeGenerator.generate()).willReturn(CODE);
        given(inviteCodeRepository.issue(eq(CODE), eq(ELDERLY_ID), any())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> careInvitationService.issueInviteCode(MEMBER_ID, ELDERLY_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVITE_CODE_ISSUE_FAILED));
    }

    // ===== 5-8 초대 코드로 연결 =====

    private void givenJoinable(boolean ownerExists) {
        givenJoinable(ownerExists, elderly(null));
    }

    private void givenJoinable(boolean ownerExists, Elderly elderly) {
        given(loginAttemptRepository.incrementInviteCode(eq(MEMBER_ID), any())).willReturn(1L);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver()));
        given(inviteCodeRepository.findElderlyId(CODE)).willReturn(Optional.of(ELDERLY_ID));
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly));
        given(careRelationshipRepository.existsByCaregiverIdAndElderlyId(CAREGIVER_ID, ELDERLY_ID)).willReturn(false);
        given(careRelationshipRepository.existsByElderlyIdAndRole(ELDERLY_ID, CareRole.OWNER)).willReturn(ownerExists);
        given(careRelationshipRepository.save(any(CareRelationship.class))).willAnswer(invocation -> {
            CareRelationship saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 31L);
            return saved;
        });
    }

    @Test
    @DisplayName("초대 코드로 연결하면 MEMBER가 되고, 쓴 코드는 지운다")
    void join_asMember() {
        // given
        givenJoinable(true);

        // when
        JoinCareRelationshipResponse response = careInvitationService.join(MEMBER_ID,
                new JoinCareRelationshipRequest("k7q2m9xd", "딸"));

        // then
        assertThat(response).isEqualTo(new JoinCareRelationshipResponse(31L, ELDERLY_ID, CareRole.MEMBER));
        ArgumentCaptor<CareRelationship> captor = ArgumentCaptor.forClass(CareRelationship.class);
        verify(careRelationshipRepository).save(captor.capture());
        assertThat(captor.getValue().getRelation()).isEqualTo("딸");
        verify(inviteCodeRepository).delete(CODE, ELDERLY_ID);
    }

    @Test
    @DisplayName("노인에게 주보호자가 없으면 초대 코드로 들어온 보호자가 OWNER가 되고, 보호자 없는 상태(파기 유예)가 풀린다")
    void join_asOwnerWhenNoOwner() {
        // given
        Elderly orphaned = elderly(null);
        orphaned.markOrphaned(LocalDateTime.now().minusDays(3));
        givenJoinable(false, orphaned);

        // when & then
        assertThat(careInvitationService.join(MEMBER_ID, new JoinCareRelationshipRequest(CODE, "아들")).role())
                .isEqualTo(CareRole.OWNER);
        assertThat(orphaned.getOrphanedAt()).isNull();
    }

    @Test
    @DisplayName("없거나 만료된 코드는 거절한다")
    void join_invalidCode() {
        // given
        given(loginAttemptRepository.incrementInviteCode(eq(MEMBER_ID), any())).willReturn(1L);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver()));
        given(inviteCodeRepository.findElderlyId(CODE)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> careInvitationService.join(MEMBER_ID, new JoinCareRelationshipRequest(CODE, "딸")))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVALID_INVITE_CODE));
        verify(careRelationshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("코드를 받은 뒤 노인이 삭제됐으면 잘못된 코드로 본다")
    void join_deletedElderly() {
        // given
        given(loginAttemptRepository.incrementInviteCode(eq(MEMBER_ID), any())).willReturn(1L);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver()));
        given(inviteCodeRepository.findElderlyId(CODE)).willReturn(Optional.of(ELDERLY_ID));
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> careInvitationService.join(MEMBER_ID, new JoinCareRelationshipRequest(CODE, "딸")))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVALID_INVITE_CODE));
    }

    @Test
    @DisplayName("이미 연결된 노인이면 거절하고 코드는 남겨 둔다 (다른 가족이 쓸 수 있도록)")
    void join_duplicate() {
        // given
        given(loginAttemptRepository.incrementInviteCode(eq(MEMBER_ID), any())).willReturn(1L);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver()));
        given(inviteCodeRepository.findElderlyId(CODE)).willReturn(Optional.of(ELDERLY_ID));
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(null)));
        given(careRelationshipRepository.existsByCaregiverIdAndElderlyId(CAREGIVER_ID, ELDERLY_ID)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> careInvitationService.join(MEMBER_ID, new JoinCareRelationshipRequest(CODE, "딸")))
                .satisfies(e -> assertErrorCode(e, ErrorCode.DUPLICATE_CARE_RELATIONSHIP));
        verify(inviteCodeRepository, never()).delete(anyString(), anyLong());
    }

    @Test
    @DisplayName("같은 회원이 10분에 10회를 넘게 시도하면 코드를 확인하지 않고 거절한다")
    void join_tooManyAttempts() {
        // given
        given(loginAttemptRepository.incrementInviteCode(eq(MEMBER_ID), any())).willReturn(11L);

        // when & then
        assertThatThrownBy(() -> careInvitationService.join(MEMBER_ID, new JoinCareRelationshipRequest(CODE, "딸")))
                .satisfies(e -> assertErrorCode(e, ErrorCode.TOO_MANY_INVITE_CODE_ATTEMPTS));
        verify(inviteCodeRepository, never()).findElderlyId(anyString());
    }

    @Test
    @DisplayName("보호자 회원이 아니면 연결할 수 없다")
    void join_notCaregiver() {
        // given
        given(loginAttemptRepository.incrementInviteCode(eq(MEMBER_ID), any())).willReturn(1L);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> careInvitationService.join(MEMBER_ID, new JoinCareRelationshipRequest(CODE, "딸")))
                .satisfies(e -> assertErrorCode(e, ErrorCode.CAREGIVER_NOT_FOUND));
    }
}
