package com.piuda.wilson.domain.elderly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.elderly.dto.ConsentItem;
import com.piuda.wilson.domain.elderly.dto.ConsentResponse;
import com.piuda.wilson.domain.elderly.dto.UpdateConsentRequest;
import com.piuda.wilson.domain.elderly.entity.Consent;
import com.piuda.wilson.domain.elderly.entity.ConsentGrantor;
import com.piuda.wilson.domain.elderly.entity.ConsentType;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.entity.LegalBasis;
import com.piuda.wilson.domain.elderly.repository.ConsentRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CAREGIVER_ID = 3L;
    private static final Long ELDERLY_ID = 15L;
    private static final ConsentItem SENSITIVE = new ConsentItem(ConsentType.SENSITIVE_INFO, true, 1);
    private static final ConsentItem SHARING = new ConsentItem(ConsentType.TRANSCRIPT_SHARING, true, 1);

    @Mock
    private ConsentRepository consentRepository;

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private CaregiverRepository caregiverRepository;

    @Mock
    private CareRelationshipRepository careRelationshipRepository;

    @InjectMocks
    private ConsentService consentService;

    private static Elderly elderly(Long memberId) {
        Member member = null;
        if (memberId != null) {
            member = Member.builder().name("김철수").memberType(MemberType.ELDERLY).build();
            ReflectionTestUtils.setField(member, "id", memberId);
        }
        Elderly elderly = Elderly.builder().member(member).name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", ELDERLY_ID);
        return elderly;
    }

    private void givenCaregiverRole(Elderly elderly, CareRole role) {
        Caregiver caregiver = Caregiver.builder().build();
        ReflectionTestUtils.setField(caregiver, "id", CAREGIVER_ID);
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly));
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver));
        given(careRelationshipRepository.findByCaregiverIdAndElderlyId(CAREGIVER_ID, ELDERLY_ID)).willReturn(
                Optional.of(CareRelationship.builder().caregiver(caregiver).elderly(elderly).role(role).build()));
    }

    private static Consent consent(ConsentType type, boolean agreed) {
        return Consent.builder().type(type).agreed(agreed).version(1).grantor(ConsentGrantor.OWNER)
                .grantedByMemberId(MEMBER_ID).legalBasis(LegalBasis.FAMILY_DELEGATION).build();
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    // ===== 등록·가입 시 최초 동의 =====

    @Test
    @DisplayName("등록 때 받은 동의를 누가·어떤 근거로 했는지와 함께 저장한다")
    void recordInitial() {
        // when
        consentService.recordInitial(elderly(null), List.of(SENSITIVE, SHARING), MEMBER_ID, ConsentGrantor.OWNER,
                LegalBasis.FAMILY_DELEGATION);

        // then
        ArgumentCaptor<Consent> captor = ArgumentCaptor.forClass(Consent.class);
        verify(consentRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Consent::getType)
                .containsExactly(ConsentType.SENSITIVE_INFO, ConsentType.TRANSCRIPT_SHARING);
        assertThat(captor.getAllValues()).allSatisfy(consent -> {
            assertThat(consent.getGrantor()).isEqualTo(ConsentGrantor.OWNER);
            assertThat(consent.getGrantedByMemberId()).isEqualTo(MEMBER_ID);
            assertThat(consent.getLegalBasis()).isEqualTo(LegalBasis.FAMILY_DELEGATION);
        });
    }

    @Test
    @DisplayName("민감정보 처리 동의가 없거나 거부하면 등록할 수 없다")
    void recordInitial_withoutSensitive() {
        // when & then
        assertThatThrownBy(() -> consentService.recordInitial(elderly(null), List.of(SHARING), MEMBER_ID,
                ConsentGrantor.OWNER, LegalBasis.FAMILY_DELEGATION))
                .satisfies(e -> assertErrorCode(e, ErrorCode.REQUIRED_CONSENT_MISSING));
        assertThatThrownBy(() -> consentService.recordInitial(elderly(null),
                List.of(new ConsentItem(ConsentType.SENSITIVE_INFO, false, 1)), MEMBER_ID,
                ConsentGrantor.OWNER, LegalBasis.FAMILY_DELEGATION))
                .satisfies(e -> assertErrorCode(e, ErrorCode.REQUIRED_CONSENT_MISSING));
        assertThatThrownBy(() -> consentService.recordInitial(elderly(null), null, MEMBER_ID,
                ConsentGrantor.OWNER, LegalBasis.FAMILY_DELEGATION))
                .satisfies(e -> assertErrorCode(e, ErrorCode.REQUIRED_CONSENT_MISSING));
        verify(consentRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 종류를 두 번 보내거나, 대리 동의인데 법적 근거가 없거나 SELF이면 잘못된 요청이다")
    void recordInitial_invalid() {
        // when & then
        assertThatThrownBy(() -> consentService.recordInitial(elderly(null), List.of(SENSITIVE, SENSITIVE),
                MEMBER_ID, ConsentGrantor.OWNER, LegalBasis.FAMILY_DELEGATION))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> consentService.recordInitial(elderly(null), List.of(SENSITIVE), MEMBER_ID,
                ConsentGrantor.OWNER, LegalBasis.SELF))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> consentService.recordInitial(elderly(null), List.of(SENSITIVE), MEMBER_ID,
                ConsentGrantor.OWNER, null))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVALID_INPUT_VALUE));
    }

    // ===== 5-12 조회 =====

    @Test
    @DisplayName("연결된 보호자는 종류별 최신 동의 상태를 본다")
    void getConsents() {
        // given
        givenCaregiverRole(elderly(null), CareRole.MEMBER);
        given(consentRepository.findByElderlyIdOrderByIdDesc(ELDERLY_ID)).willReturn(List.of(
                consent(ConsentType.TRANSCRIPT_SHARING, false),
                consent(ConsentType.SENSITIVE_INFO, true),
                consent(ConsentType.TRANSCRIPT_SHARING, true)));

        // when
        List<ConsentResponse> consents = consentService.getConsents(MEMBER_ID, ELDERLY_ID);

        // then
        assertThat(consents).extracting(ConsentResponse::type, ConsentResponse::agreed).containsExactly(
                tuple(ConsentType.TRANSCRIPT_SHARING, false),
                tuple(ConsentType.SENSITIVE_INFO, true));
    }

    @Test
    @DisplayName("연결되지 않은 보호자는 동의를 볼 수 없다")
    void getConsents_stranger() {
        // given
        Caregiver caregiver = Caregiver.builder().build();
        ReflectionTestUtils.setField(caregiver, "id", CAREGIVER_ID);
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(null)));
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver));
        given(careRelationshipRepository.findByCaregiverIdAndElderlyId(CAREGIVER_ID, ELDERLY_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> consentService.getConsents(MEMBER_ID, ELDERLY_ID))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
    }

    // ===== 5-12 변경 =====

    @Test
    @DisplayName("주보호자는 법적 근거와 함께 대리로 동의를 바꾼다")
    void update_byOwner() {
        // given
        givenCaregiverRole(elderly(null), CareRole.OWNER);

        // when
        consentService.updateConsent(MEMBER_ID, ELDERLY_ID, ConsentType.TRANSCRIPT_SHARING,
                new UpdateConsentRequest(true, 1, LegalBasis.GUARDIANSHIP));

        // then
        ArgumentCaptor<Consent> captor = ArgumentCaptor.forClass(Consent.class);
        verify(consentRepository).save(captor.capture());
        assertThat(captor.getValue().getGrantor()).isEqualTo(ConsentGrantor.OWNER);
        assertThat(captor.getValue().getLegalBasis()).isEqualTo(LegalBasis.GUARDIANSHIP);
        assertThat(captor.getValue().isAgreed()).isTrue();
    }

    @Test
    @DisplayName("주보호자의 대리 변경에는 법적 근거가 필요하다")
    void update_byOwner_withoutLegalBasis() {
        // given
        givenCaregiverRole(elderly(null), CareRole.OWNER);

        // when & then
        assertThatThrownBy(() -> consentService.updateConsent(MEMBER_ID, ELDERLY_ID, ConsentType.TRANSCRIPT_SHARING,
                new UpdateConsentRequest(true, 1, null)))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    @DisplayName("일반 보호자(MEMBER)는 동의를 바꿀 수 없다")
    void update_byMember_forbidden() {
        // given
        givenCaregiverRole(elderly(null), CareRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> consentService.updateConsent(MEMBER_ID, ELDERLY_ID, ConsentType.TRANSCRIPT_SHARING,
                new UpdateConsentRequest(true, 1, LegalBasis.FAMILY_DELEGATION)))
                .satisfies(e -> assertErrorCode(e, ErrorCode.NO_PERMISSION));
        verify(consentRepository, never()).save(any());
    }

    @Test
    @DisplayName("노인 본인은 법적 근거 없이 본인 동의(SELF)로 바꾼다")
    void update_bySelf() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly(MEMBER_ID)));
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        // when
        consentService.updateConsent(MEMBER_ID, ELDERLY_ID, ConsentType.TRANSCRIPT_SHARING,
                new UpdateConsentRequest(false, 1, null));

        // then
        ArgumentCaptor<Consent> captor = ArgumentCaptor.forClass(Consent.class);
        verify(consentRepository).save(captor.capture());
        assertThat(captor.getValue().getGrantor()).isEqualTo(ConsentGrantor.SELF);
        assertThat(captor.getValue().getLegalBasis()).isEqualTo(LegalBasis.SELF);
        assertThat(captor.getValue().isAgreed()).isFalse();
    }

    @Test
    @DisplayName("민감정보 처리 동의는 철회할 수 없다 (철회는 노인 삭제로 처리)")
    void update_revokeSensitive() {
        // given
        givenCaregiverRole(elderly(null), CareRole.OWNER);

        // when & then
        assertThatThrownBy(() -> consentService.updateConsent(MEMBER_ID, ELDERLY_ID, ConsentType.SENSITIVE_INFO,
                new UpdateConsentRequest(false, 1, LegalBasis.FAMILY_DELEGATION)))
                .satisfies(e -> assertErrorCode(e, ErrorCode.REQUIRED_CONSENT_NOT_REVOCABLE));
    }

    // ===== 다른 기능에서 쓰는 확인 =====

    @Test
    @DisplayName("가장 최근 기록이 동의일 때만 동의한 것으로 본다")
    void isAgreed() {
        // given
        given(consentRepository.findFirstByElderlyIdAndTypeOrderByIdDesc(ELDERLY_ID, ConsentType.TRANSCRIPT_SHARING))
                .willReturn(Optional.of(consent(ConsentType.TRANSCRIPT_SHARING, true)));
        given(consentRepository.findFirstByElderlyIdAndTypeOrderByIdDesc(ELDERLY_ID, ConsentType.RESEARCH_RETENTION))
                .willReturn(Optional.empty());

        // when & then
        assertThat(consentService.isAgreed(ELDERLY_ID, ConsentType.TRANSCRIPT_SHARING)).isTrue();
        assertThat(consentService.isAgreed(ELDERLY_ID, ConsentType.RESEARCH_RETENTION)).isFalse();
    }
}
