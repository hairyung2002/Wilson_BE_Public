package com.piuda.wilson.domain.elderly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ElderlyAccessServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ELDERLY_ID = 15L;

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private CaregiverRepository caregiverRepository;

    @Mock
    private CareRelationshipRepository careRelationshipRepository;

    @InjectMocks
    private ElderlyAccessService elderlyAccessService;

    private Elderly givenElderly(Long elderlyMemberId) {
        Member member = null;
        if (elderlyMemberId != null) {
            member = Member.builder().name("김철수").memberType(MemberType.ELDERLY).build();
            ReflectionTestUtils.setField(member, "id", elderlyMemberId);
        }
        Elderly elderly = Elderly.builder().member(member).name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", ELDERLY_ID);
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly));
        return elderly;
    }

    private void givenCaregiver(CareRole role) {
        Caregiver caregiver = Caregiver.builder().build();
        ReflectionTestUtils.setField(caregiver, "id", 3L);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver));
        given(careRelationshipRepository.findByCaregiverIdAndElderlyId(3L, ELDERLY_ID)).willReturn(role == null
                ? Optional.empty() : Optional.of(CareRelationship.builder().caregiver(caregiver).role(role).build()));
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    @Test
    @DisplayName("조회는 연결된 보호자(OWNER·MEMBER)와 노인 본인이 할 수 있다")
    void requireViewer() {
        // given
        Elderly elderly = givenElderly(MEMBER_ID);

        // when
        ElderlyAccess access = elderlyAccessService.requireViewer(MEMBER_ID, ELDERLY_ID);

        // then
        assertThat(access.elderly()).isSameAs(elderly);
        assertThat(access.actor()).isEqualTo(AccessActor.SELF);
    }

    @Test
    @DisplayName("MEMBER도 조회할 수 있다")
    void requireViewer_member() {
        // given
        givenElderly(null);
        givenCaregiver(CareRole.MEMBER);

        // when & then
        assertThat(elderlyAccessService.requireViewer(MEMBER_ID, ELDERLY_ID).actor()).isEqualTo(AccessActor.MEMBER);
    }

    @Test
    @DisplayName("관리(설정 변경 등)는 OWNER와 노인 본인만 할 수 있다")
    void requireManager() {
        // given
        givenElderly(null);
        givenCaregiver(CareRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> elderlyAccessService.requireManager(MEMBER_ID, ELDERLY_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("보호자 전용 화면(판정 이력·대시보드)은 연결된 보호자만 볼 수 있고, 노인 본인은 볼 수 없다")
    void requireCaregiver() {
        // given
        givenElderly(MEMBER_ID);

        // when & then
        assertThatThrownBy(() -> elderlyAccessService.requireCaregiver(MEMBER_ID, ELDERLY_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("MEMBER도 보호자 전용 화면을 볼 수 있다")
    void requireCaregiver_member() {
        // given
        givenElderly(null);
        givenCaregiver(CareRole.MEMBER);

        // when & then
        assertThat(elderlyAccessService.requireCaregiver(MEMBER_ID, ELDERLY_ID).actor()).isEqualTo(AccessActor.MEMBER);
    }

    @Test
    @DisplayName("연결되지 않은 보호자, 다른 노인 회원은 접근할 수 없다")
    void stranger() {
        // given
        givenElderly(99L);
        givenCaregiver(null);

        // when & then
        assertThatThrownBy(() -> elderlyAccessService.requireViewer(MEMBER_ID, ELDERLY_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("삭제됐거나 없는 노인은 404")
    void notFound() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> elderlyAccessService.requireViewer(MEMBER_ID, ELDERLY_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.ELDERLY_NOT_FOUND));
    }
}
