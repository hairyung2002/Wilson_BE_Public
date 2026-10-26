package com.piuda.wilson.domain.caregiver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.caregiver.dto.CaregiverResponse;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CareRelationshipServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ELDERLY_ID = 15L;

    @Mock
    private CaregiverRepository caregiverRepository;

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private CareRelationshipRepository careRelationshipRepository;

    @InjectMocks
    private CareRelationshipService careRelationshipService;

    private final Elderly elderly = withId(Elderly.builder().name("김철수").build(), ELDERLY_ID);
    private final Caregiver me = caregiver(3L, "김보호");
    private final Caregiver sister = caregiver(4L, "김딸");

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static Caregiver caregiver(Long id, String name) {
        Member member = Member.builder().email(name + "@wilson.com").name(name).memberType(MemberType.CAREGIVER).build();
        return withId(Caregiver.builder().member(member).build(), id);
    }

    private static CareRelationship relationship(Long id, Caregiver caregiver, Elderly elderly, CareRole role) {
        return withId(CareRelationship.builder().caregiver(caregiver).elderly(elderly).relation("가족").role(role)
                .build(), id);
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    private CareRelationship givenMine(CareRole role) {
        CareRelationship mine = relationship(30L, me, elderly, role);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(me));
        given(careRelationshipRepository.findByCaregiverIdAndElderlyId(3L, ELDERLY_ID)).willReturn(Optional.of(mine));
        return mine;
    }

    // ===== 5-9 보호자 목록 =====

    @Test
    @DisplayName("연결된 보호자는 노인의 보호자 목록을 역할과 함께 본다")
    void getCaregivers() {
        // given
        CareRelationship mine = givenMine(CareRole.OWNER);
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly));
        given(careRelationshipRepository.findAllWithCaregiverByElderlyId(ELDERLY_ID))
                .willReturn(List.of(mine, relationship(31L, sister, elderly, CareRole.MEMBER)));

        // when
        List<CaregiverResponse> caregivers = careRelationshipService.getCaregivers(MEMBER_ID, ELDERLY_ID);

        // then
        assertThat(caregivers).containsExactly(
                new CaregiverResponse(30L, "김보호", "가족", CareRole.OWNER, true),
                new CaregiverResponse(31L, "김딸", "가족", CareRole.MEMBER, false));
    }

    @Test
    @DisplayName("연결되지 않은 보호자는 목록을 볼 수 없다")
    void getCaregivers_notLinked() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly));
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(me));
        given(careRelationshipRepository.findByCaregiverIdAndElderlyId(3L, ELDERLY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> careRelationshipService.getCaregivers(MEMBER_ID, ELDERLY_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    // ===== 5-10 주보호자 넘기기 =====

    @Test
    @DisplayName("주보호자를 넘기면 대상이 OWNER가 되고 나는 MEMBER가 된다 (기존 OWNER를 먼저 내린다)")
    void transferOwner() {
        // given
        CareRelationship mine = givenMine(CareRole.OWNER);
        CareRelationship target = relationship(31L, sister, elderly, CareRole.MEMBER);
        given(careRelationshipRepository.findById(31L)).willReturn(Optional.of(target));

        // when
        careRelationshipService.transferOwner(MEMBER_ID, ELDERLY_ID, 31L);

        // then
        assertThat(mine.getRole()).isEqualTo(CareRole.MEMBER);
        assertThat(target.getRole()).isEqualTo(CareRole.OWNER);
        verify(careRelationshipRepository).flush();
    }

    @Test
    @DisplayName("MEMBER는 주보호자를 넘길 수 없다")
    void transferOwner_notOwner() {
        // given
        givenMine(CareRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> careRelationshipService.transferOwner(MEMBER_ID, ELDERLY_ID, 31L))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("다른 노인의 보호자에게는 주보호자를 넘길 수 없다")
    void transferOwner_targetOfOtherElderly() {
        // given
        givenMine(CareRole.OWNER);
        Elderly other = withId(Elderly.builder().name("이영희").build(), 99L);
        given(careRelationshipRepository.findById(31L))
                .willReturn(Optional.of(relationship(31L, sister, other, CareRole.MEMBER)));

        // when & then
        assertThatThrownBy(() -> careRelationshipService.transferOwner(MEMBER_ID, ELDERLY_ID, 31L))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVALID_OWNER_TRANSFER_TARGET));
    }

    @Test
    @DisplayName("나 자신(이미 OWNER)에게는 넘길 수 없다")
    void transferOwner_toSelf() {
        // given
        CareRelationship mine = givenMine(CareRole.OWNER);
        given(careRelationshipRepository.findById(30L)).willReturn(Optional.of(mine));

        // when & then
        assertThatThrownBy(() -> careRelationshipService.transferOwner(MEMBER_ID, ELDERLY_ID, 30L))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVALID_OWNER_TRANSFER_TARGET));
        assertThat(mine.getRole()).isEqualTo(CareRole.OWNER);
    }

    // ===== 5-11 연결 해제 =====

    @Test
    @DisplayName("MEMBER는 스스로 연결을 끊고 나갈 수 있다")
    void unlink_memberLeaves() {
        // given
        CareRelationship mine = relationship(31L, me, elderly, CareRole.MEMBER);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(me));
        given(careRelationshipRepository.findById(31L)).willReturn(Optional.of(mine));

        // when
        careRelationshipService.unlink(MEMBER_ID, 31L);

        // then
        verify(careRelationshipRepository).delete(mine);
    }

    @Test
    @DisplayName("OWNER는 MEMBER를 내보낼 수 있다")
    void unlink_ownerRemovesMember() {
        // given
        CareRelationship target = relationship(31L, sister, elderly, CareRole.MEMBER);
        givenMine(CareRole.OWNER);
        given(careRelationshipRepository.findById(31L)).willReturn(Optional.of(target));

        // when
        careRelationshipService.unlink(MEMBER_ID, 31L);

        // then
        verify(careRelationshipRepository).delete(target);
    }

    @Test
    @DisplayName("OWNER는 주보호자를 넘기기 전에는 스스로 연결을 끊을 수 없다")
    void unlink_ownerSelf() {
        // given
        CareRelationship mine = relationship(30L, me, elderly, CareRole.OWNER);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(me));
        given(careRelationshipRepository.findById(30L)).willReturn(Optional.of(mine));

        // when & then
        assertThatThrownBy(() -> careRelationshipService.unlink(MEMBER_ID, 30L))
                .satisfies(e -> assertErrorCode(e, ErrorCode.OWNER_TRANSFER_REQUIRED));
        verify(careRelationshipRepository, never()).delete(any());
    }

    @Test
    @DisplayName("MEMBER는 다른 보호자를 내보낼 수 없다")
    void unlink_memberRemovesOther() {
        // given
        CareRelationship target = relationship(32L, sister, elderly, CareRole.MEMBER);
        givenMine(CareRole.MEMBER);
        given(careRelationshipRepository.findById(32L)).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> careRelationshipService.unlink(MEMBER_ID, 32L))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("없는 연결은 404")
    void unlink_notFound() {
        // given
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(me));
        given(careRelationshipRepository.findById(77L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> careRelationshipService.unlink(MEMBER_ID, 77L))
                .satisfies(e -> assertErrorCode(e, ErrorCode.CARE_RELATIONSHIP_NOT_FOUND));
    }
}
