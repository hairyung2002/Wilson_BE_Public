package com.piuda.wilson.domain.elderly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.auth.repository.DeviceCodeRepository;
import com.piuda.wilson.domain.auth.service.DeviceCodeGenerator;
import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.elderly.dto.ConsentItem;
import com.piuda.wilson.domain.elderly.dto.CreateElderlyRequest;
import com.piuda.wilson.domain.elderly.dto.CreateElderlyResponse;
import com.piuda.wilson.domain.elderly.dto.DeviceCodeResponse;
import com.piuda.wilson.domain.elderly.dto.ElderlyDetailResponse;
import com.piuda.wilson.domain.elderly.dto.ElderlySummaryResponse;
import com.piuda.wilson.domain.elderly.dto.UpdateElderlyRequest;
import com.piuda.wilson.domain.elderly.entity.ConsentGrantor;
import com.piuda.wilson.domain.elderly.entity.ConsentType;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.entity.Gender;
import com.piuda.wilson.domain.elderly.entity.LegalBasis;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
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
class ElderlyServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CAREGIVER_ID = 3L;
    private static final Long ELDERLY_ID = 15L;
    private static final LocalDate BIRTH = LocalDate.of(1945, 3, 11);
    private static final Duration DEVICE_CODE_TTL = Duration.ofMinutes(10);
    private static final List<ConsentItem> CONSENTS = List.of(new ConsentItem(ConsentType.SENSITIVE_INFO, true, 1));

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private CaregiverRepository caregiverRepository;

    @Mock
    private CareRelationshipRepository careRelationshipRepository;

    @Mock
    private DeviceCodeRepository deviceCodeRepository;

    @Mock
    private DeviceCodeGenerator deviceCodeGenerator;

    @Mock
    private ConsentService consentService;

    @Mock
    private ElderlyDeletionService elderlyDeletionService;

    @InjectMocks
    private ElderlyService elderlyService;

    private Caregiver caregiver() {
        Member member = Member.builder()
                .email("care@wilson.com").password("p").name("보호자").memberType(MemberType.CAREGIVER).build();
        Caregiver caregiver = Caregiver.builder().member(member).build();
        ReflectionTestUtils.setField(caregiver, "id", CAREGIVER_ID);
        return caregiver;
    }

    private Elderly elderly() {
        Elderly elderly = Elderly.builder().name("김철수").birthDate(BIRTH).gender(Gender.MALE).build();
        ReflectionTestUtils.setField(elderly, "id", ELDERLY_ID);
        return elderly;
    }

    private CareRelationship relationship(Caregiver caregiver, Elderly elderly) {
        return relationship(caregiver, elderly, CareRole.OWNER);
    }

    private CareRelationship relationship(Caregiver caregiver, Elderly elderly, CareRole role) {
        return CareRelationship.builder()
                .caregiver(caregiver).elderly(elderly).relation("FATHER").role(role).build();
    }

    private Elderly givenAuthorizedElderly() {
        return givenAuthorizedElderly(CareRole.OWNER);
    }

    private Elderly givenAuthorizedElderly(CareRole role) {
        Caregiver caregiver = caregiver();
        Elderly elderly = elderly();
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver));
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly));
        given(careRelationshipRepository.findByCaregiverIdAndElderlyId(CAREGIVER_ID, ELDERLY_ID))
                .willReturn(Optional.of(relationship(caregiver, elderly, role)));
        return elderly;
    }

    private void givenUnlinkedElderly(Elderly elderly) {
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver()));
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly));
        given(careRelationshipRepository.findByCaregiverIdAndElderlyId(CAREGIVER_ID, ELDERLY_ID))
                .willReturn(Optional.empty());
    }

    @Test
    @DisplayName("보호자가 노인을 등록하면 노인과 주보호자(OWNER) 관계가 생성된다")
    void register_success() {
        // given
        CreateElderlyRequest request = new CreateElderlyRequest("김철수", BIRTH, Gender.MALE, "FATHER", CONSENTS, LegalBasis.FAMILY_DELEGATION);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver()));
        given(elderlyRepository.save(any(Elderly.class))).willReturn(elderly());

        // when
        CreateElderlyResponse response = elderlyService.register(MEMBER_ID, request);

        // then
        assertThat(response.elderlyId()).isEqualTo(ELDERLY_ID);
        ArgumentCaptor<CareRelationship> captor = ArgumentCaptor.forClass(CareRelationship.class);
        verify(careRelationshipRepository).save(captor.capture());
        assertThat(captor.getValue().getRelation()).isEqualTo("FATHER");
        assertThat(captor.getValue().getRole()).isEqualTo(CareRole.OWNER);
        assertThat(captor.getValue().getElderly().getId()).isEqualTo(ELDERLY_ID);
        verify(consentService).recordInitial(any(Elderly.class), eq(CONSENTS), eq(MEMBER_ID),
                eq(ConsentGrantor.OWNER), eq(LegalBasis.FAMILY_DELEGATION));
    }

    @Test
    @DisplayName("보호자 프로필이 없으면 노인을 등록할 수 없다")
    void register_caregiverNotFound_throws() {
        // given
        CreateElderlyRequest request = new CreateElderlyRequest("김철수", BIRTH, Gender.MALE, "FATHER", CONSENTS, LegalBasis.FAMILY_DELEGATION);
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> elderlyService.register(MEMBER_ID, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CAREGIVER_NOT_FOUND));
        verify(elderlyRepository, never()).save(any());
    }

    @Test
    @DisplayName("연결된 노인 목록을 나이와 관계와 함께 조회한다")
    void getElderlies_success() {
        // given
        Caregiver caregiver = caregiver();
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver));
        given(careRelationshipRepository.findAllWithActiveElderlyByCaregiverId(CAREGIVER_ID))
                .willReturn(List.of(relationship(caregiver, elderly())));

        // when
        List<ElderlySummaryResponse> responses = elderlyService.getElderlies(MEMBER_ID);

        // then
        assertThat(responses).hasSize(1);
        ElderlySummaryResponse summary = responses.get(0);
        assertThat(summary.id()).isEqualTo(ELDERLY_ID);
        assertThat(summary.name()).isEqualTo("김철수");
        assertThat(summary.age()).isEqualTo(Period.between(BIRTH, LocalDate.now()).getYears());
        assertThat(summary.relationship()).isEqualTo("FATHER");
    }

    @Test
    @DisplayName("등록된 노인이 없으면 빈 목록을 반환한다")
    void getElderlies_empty() {
        // given
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver()));
        given(careRelationshipRepository.findAllWithActiveElderlyByCaregiverId(CAREGIVER_ID))
                .willReturn(List.of());

        // when
        List<ElderlySummaryResponse> responses = elderlyService.getElderlies(MEMBER_ID);

        // then
        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("연결된 노인의 상세 정보를 조회한다")
    void getElderly_success() {
        // given
        givenAuthorizedElderly();

        // when
        ElderlyDetailResponse response = elderlyService.getElderly(MEMBER_ID, ELDERLY_ID);

        // then
        assertThat(response.id()).isEqualTo(ELDERLY_ID);
        assertThat(response.name()).isEqualTo("김철수");
        assertThat(response.birth()).isEqualTo(BIRTH);
        assertThat(response.gender()).isEqualTo(Gender.MALE);
        assertThat(response.relationship()).isEqualTo("FATHER");
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 노인을 조회하면 예외를 던진다")
    void getElderly_notFound_throws() {
        // given
        given(caregiverRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(caregiver()));
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> elderlyService.getElderly(MEMBER_ID, ELDERLY_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ELDERLY_NOT_FOUND));
    }

    @Test
    @DisplayName("연결되지 않은 노인을 조회하면 권한 예외를 던진다")
    void getElderly_noPermission_throws() {
        // given
        givenUnlinkedElderly(elderly());

        // when & then
        assertThatThrownBy(() -> elderlyService.getElderly(MEMBER_ID, ELDERLY_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("요청한 항목만 수정하고 나머지는 유지한다")
    void update_success() {
        // given
        givenAuthorizedElderly();
        UpdateElderlyRequest request = new UpdateElderlyRequest("김철순", null, "GRANDFATHER");

        // when
        ElderlyDetailResponse response = elderlyService.update(MEMBER_ID, ELDERLY_ID, request);

        // then
        assertThat(response.name()).isEqualTo("김철순");
        assertThat(response.birth()).isEqualTo(BIRTH);
        assertThat(response.relationship()).isEqualTo("GRANDFATHER");
    }

    @Test
    @DisplayName("연결되지 않은 노인은 수정할 수 없다")
    void update_noPermission_throws() {
        // given
        Elderly elderly = elderly();
        givenUnlinkedElderly(elderly);
        UpdateElderlyRequest request = new UpdateElderlyRequest("김철순", null, null);

        // when & then
        assertThatThrownBy(() -> elderlyService.update(MEMBER_ID, ELDERLY_ID, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NO_PERMISSION));
        assertThat(elderly.getName()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("주보호자가 노인을 삭제하면 개인정보·원문 파기 정책으로 지운다")
    void delete_success() {
        // given
        givenAuthorizedElderly();

        // when
        elderlyService.delete(MEMBER_ID, ELDERLY_ID);

        // then
        verify(elderlyDeletionService).erase(ELDERLY_ID);
        verify(elderlyRepository, never()).delete(any());
    }

    @Test
    @DisplayName("일반 보호자(MEMBER)는 노인을 삭제할 수 없다")
    void delete_byMember_forbidden() {
        // given
        Elderly elderly = givenAuthorizedElderly(CareRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> elderlyService.delete(MEMBER_ID, ELDERLY_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NO_PERMISSION));
        assertThat(elderly.isDeleted()).isFalse();
        verify(elderlyDeletionService, never()).erase(any());
    }

    @Test
    @DisplayName("연결되지 않은 노인은 삭제할 수 없다")
    void delete_noPermission_throws() {
        // given
        Elderly elderly = elderly();
        givenUnlinkedElderly(elderly);

        // when & then
        assertThatThrownBy(() -> elderlyService.delete(MEMBER_ID, ELDERLY_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NO_PERMISSION));
        assertThat(elderly.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("연결된 노인의 기기 연결 코드를 10분 유효기간으로 발급한다")
    void issueDeviceCode_success() {
        // given
        givenAuthorizedElderly();
        given(deviceCodeGenerator.generate()).willReturn("123456");
        given(deviceCodeRepository.saveIfAbsent("123456", ELDERLY_ID, DEVICE_CODE_TTL)).willReturn(true);

        // when
        DeviceCodeResponse response = elderlyService.issueDeviceCode(MEMBER_ID, ELDERLY_ID);

        // then
        assertThat(response.deviceCode()).isEqualTo("123456");
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now().plusMinutes(9));
    }

    @Test
    @DisplayName("이미 사용 중인 코드와 겹치면 새 코드로 다시 시도한다")
    void issueDeviceCode_collision_retries() {
        // given
        givenAuthorizedElderly();
        given(deviceCodeGenerator.generate()).willReturn("111111", "222222");
        given(deviceCodeRepository.saveIfAbsent("111111", ELDERLY_ID, DEVICE_CODE_TTL)).willReturn(false);
        given(deviceCodeRepository.saveIfAbsent("222222", ELDERLY_ID, DEVICE_CODE_TTL)).willReturn(true);

        // when
        DeviceCodeResponse response = elderlyService.issueDeviceCode(MEMBER_ID, ELDERLY_ID);

        // then
        assertThat(response.deviceCode()).isEqualTo("222222");
    }

    @Test
    @DisplayName("재시도 후에도 코드를 저장하지 못하면 예외를 던진다")
    void issueDeviceCode_exhausted_throws() {
        // given
        givenAuthorizedElderly();
        given(deviceCodeGenerator.generate()).willReturn("111111");
        given(deviceCodeRepository.saveIfAbsent(eq("111111"), eq(ELDERLY_ID), any())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> elderlyService.issueDeviceCode(MEMBER_ID, ELDERLY_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEVICE_CODE_ISSUE_FAILED));
    }

    @Test
    @DisplayName("연결되지 않은 노인의 기기 연결 코드는 발급할 수 없다")
    void issueDeviceCode_noPermission_throws() {
        // given
        givenUnlinkedElderly(elderly());

        // when & then
        assertThatThrownBy(() -> elderlyService.issueDeviceCode(MEMBER_ID, ELDERLY_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NO_PERMISSION));
        verify(deviceCodeGenerator, never()).generate();
    }
}
