package com.piuda.wilson.domain.elderly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.analysis.repository.CognitiveMetricRepository;
import com.piuda.wilson.domain.analysis.repository.DailyAssessmentRepository;
import com.piuda.wilson.domain.analysis.repository.ElderBaselineRepository;
import com.piuda.wilson.domain.auth.service.AuthService;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.conversation.repository.MessageRepository;
import com.piuda.wilson.domain.conversation.repository.ProactiveTriggerRepository;
import com.piuda.wilson.domain.conversation.service.ConversationContextService;
import com.piuda.wilson.domain.elderly.entity.ConsentType;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.entity.Gender;
import com.piuda.wilson.domain.elderly.entity.SubjectErasure;
import com.piuda.wilson.domain.elderly.repository.ConsentRepository;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.elderly.repository.SubjectErasureRepository;
import com.piuda.wilson.domain.emergency.repository.EmergencyRepository;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.schedule.repository.ScheduleRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ElderlyDeletionServiceTest {

    private static final Long ELDERLY_ID = 15L;
    private static final Long ELDERLY_MEMBER_ID = 7L;

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private CareRelationshipRepository careRelationshipRepository;

    @Mock
    private ConsentRepository consentRepository;

    @Mock
    private CognitiveMetricRepository cognitiveMetricRepository;

    @Mock
    private ElderBaselineRepository elderBaselineRepository;

    @Mock
    private DailyAssessmentRepository dailyAssessmentRepository;

    @Mock
    private EmergencyRepository emergencyRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ProactiveTriggerRepository proactiveTriggerRepository;

    @Mock
    private ConsentService consentService;

    @Mock
    private ConversationContextService conversationContextService;

    @Mock
    private AuthService authService;

    @Mock
    private SubjectErasureRepository subjectErasureRepository;

    @InjectMocks
    private ElderlyDeletionService elderlyDeletionService;

    private Elderly givenElderly(boolean researchRetention) {
        Member member = Member.builder().name("김철수").memberType(MemberType.ELDERLY).build();
        ReflectionTestUtils.setField(member, "id", ELDERLY_MEMBER_ID);
        Elderly elderly = Elderly.builder().member(member).name("김철수").birthDate(LocalDate.of(1945, 3, 11))
                .gender(Gender.MALE).careNote("고혈압").build();
        ReflectionTestUtils.setField(elderly, "id", ELDERLY_ID);
        given(elderlyRepository.findById(ELDERLY_ID)).willReturn(Optional.of(elderly));
        given(consentService.isAgreed(ELDERLY_ID, ConsentType.RESEARCH_RETENTION)).willReturn(researchRetention);
        given(conversationRepository.findIdsByElderlyId(ELDERLY_ID)).willReturn(List.of(100L, 101L));
        return elderly;
    }

    @Test
    @DisplayName("실명·생년월일·성별·케어 메모를 파기하고, 노인 계정을 탈퇴 처리해 모든 토큰을 끊는다")
    void erase_personalData() {
        // given
        Elderly elderly = givenElderly(false);
        Member member = elderly.getMember();

        // when
        elderlyDeletionService.erase(ELDERLY_ID);

        // then
        assertThat(elderly.isDeleted()).isTrue();
        assertThat(elderly.getName()).isEqualTo("삭제된 어르신");
        assertThat(elderly.getBirthDate()).isNull();
        assertThat(elderly.getGender()).isNull();
        assertThat(elderly.getCareNote()).isNull();
        assertThat(member.isDeleted()).isTrue();
        verify(authService).logout(ELDERLY_MEMBER_ID);
    }

    @Test
    @DisplayName("대화 원문·대화 맥락·보호자 연결·일정·동의 기록·기준값·판정 이력·응급 기록을 파기하고 대화는 숨긴다")
    void erase_conversationsAndLinks() {
        // given
        givenElderly(false);

        // when
        elderlyDeletionService.erase(ELDERLY_ID);

        // then
        verify(messageRepository).deleteByConversationIdIn(List.of(100L, 101L));
        verify(conversationRepository).softDeleteAllByElderlyId(ELDERLY_ID);
        verify(conversationContextService).clear(100L);
        verify(conversationContextService).clear(101L);
        verify(careRelationshipRepository).deleteByElderlyId(ELDERLY_ID);
        verify(scheduleRepository).deleteByElderlyId(ELDERLY_ID);
        verify(proactiveTriggerRepository).deleteByElderlyId(ELDERLY_ID);
        verify(consentRepository).deleteByElderlyId(ELDERLY_ID);
        verify(elderBaselineRepository).deleteByElderlyId(ELDERLY_ID);
        verify(dailyAssessmentRepository).deleteByElderlyId(ELDERLY_ID);
        verify(emergencyRepository).deleteByElderlyId(ELDERLY_ID);
    }

    @Test
    @DisplayName("AI 쪽 개인 임베딩 파기를 바꾸기 전 가명 식별자로 요청해 둔다 (발행은 잡이 성공할 때까지 재시도, J3)")
    void erase_requestsAiErasure() {
        // given
        Elderly elderly = givenElderly(true);
        UUID originalSubject = elderly.getSubjectId();

        // when
        elderlyDeletionService.erase(ELDERLY_ID);

        // then
        ArgumentCaptor<SubjectErasure> request = ArgumentCaptor.forClass(SubjectErasure.class);
        verify(subjectErasureRepository).save(request.capture());
        assertThat(request.getValue().getSubjectId()).isEqualTo(originalSubject);
    }

    @Test
    @DisplayName("연구 보관 동의가 없으면 인지지표도 지운다")
    void erase_withoutResearchRetention_deletesMetrics() {
        // given
        Elderly elderly = givenElderly(false);
        UUID originalSubject = elderly.getSubjectId();

        // when
        elderlyDeletionService.erase(ELDERLY_ID);

        // then
        verify(cognitiveMetricRepository).deleteBySubjectId(originalSubject);
    }

    @Test
    @DisplayName("연구 보관 동의가 있으면 지표는 남기고, 가명 식별자를 바꿔 노인과의 연결만 끊는다")
    void erase_withResearchRetention_keepsMetricsAndCutsLink() {
        // given
        Elderly elderly = givenElderly(true);
        UUID originalSubject = elderly.getSubjectId();

        // when
        elderlyDeletionService.erase(ELDERLY_ID);

        // then
        verify(cognitiveMetricRepository, never()).deleteBySubjectId(any());
        assertThat(elderly.getSubjectId()).isNotNull().isNotEqualTo(originalSubject);
    }

    @Test
    @DisplayName("보호자가 등록한 뒤 기기 로그인을 한 적 없는 노인(계정 없음)도 지운다")
    void erase_withoutMember() {
        // given
        Elderly elderly = Elderly.builder().name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", ELDERLY_ID);
        given(elderlyRepository.findById(ELDERLY_ID)).willReturn(Optional.of(elderly));
        given(conversationRepository.findIdsByElderlyId(ELDERLY_ID)).willReturn(List.of());

        // when
        elderlyDeletionService.erase(ELDERLY_ID);

        // then
        assertThat(elderly.isDeleted()).isTrue();
        verify(authService, never()).logout(any());
        verify(messageRepository, never()).deleteByConversationIdIn(any());
    }
}
