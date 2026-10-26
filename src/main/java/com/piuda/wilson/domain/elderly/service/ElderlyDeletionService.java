package com.piuda.wilson.domain.elderly.service;

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
import com.piuda.wilson.domain.elderly.entity.SubjectErasure;
import com.piuda.wilson.domain.elderly.repository.ConsentRepository;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.elderly.repository.SubjectErasureRepository;
import com.piuda.wilson.domain.emergency.repository.EmergencyRepository;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.schedule.repository.ScheduleRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노인 삭제 (api-spec 5-5, decisions.md B5). 숨김 처리만 하지 않고, 재식별될 수 있는 것은 파기한다.
 *
 * <p>실명·생년월일·대화 원문·대화 맥락·보호자 연결·일정·동의 기록·기준값·판정 이력·응급 기록은 지우고, 노인 계정은 탈퇴 처리한다.
 * 인지지표는 연구 보관 동의가 있을 때만 가명으로 남기며, 가명 식별자를 바꿔 노인과의 연결을 끊는다.
 * AI 쪽 RAG 개인 임베딩은 바꾸기 전 가명 식별자로 파기를 요청해 두고, {@link SubjectErasurePublishJob}이
 * 발행에 성공할 때까지 다시 보낸다 (결정 J3).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElderlyDeletionService {

    private final ElderlyRepository elderlyRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final CareRelationshipRepository careRelationshipRepository;
    private final ScheduleRepository scheduleRepository;
    private final ProactiveTriggerRepository proactiveTriggerRepository;
    private final ConsentRepository consentRepository;
    private final CognitiveMetricRepository cognitiveMetricRepository;
    private final ElderBaselineRepository elderBaselineRepository;
    private final DailyAssessmentRepository dailyAssessmentRepository;
    private final EmergencyRepository emergencyRepository;
    private final ConsentService consentService;
    private final ConversationContextService conversationContextService;
    private final AuthService authService;
    private final SubjectErasureRepository subjectErasureRepository;

    @Transactional
    public void erase(Long elderlyId) {
        Elderly elderly = elderlyRepository.findById(elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ELDERLY_NOT_FOUND));
        UUID subjectId = elderly.getSubjectId();
        boolean keepMetrics = consentService.isAgreed(elderlyId, ConsentType.RESEARCH_RETENTION);

        List<Long> conversationIds = eraseConversations(elderlyId);
        careRelationshipRepository.deleteByElderlyId(elderlyId);
        scheduleRepository.deleteByElderlyId(elderlyId);
        proactiveTriggerRepository.deleteByElderlyId(elderlyId);
        consentRepository.deleteByElderlyId(elderlyId);
        elderBaselineRepository.deleteByElderlyId(elderlyId);
        dailyAssessmentRepository.deleteByElderlyId(elderlyId);
        emergencyRepository.deleteByElderlyId(elderlyId);
        if (!keepMetrics) {
            cognitiveMetricRepository.deleteBySubjectId(subjectId);
        }
        subjectErasureRepository.save(SubjectErasure.of(subjectId));
        Member member = elderly.getMember();
        if (member != null) {
            member.withdraw();
        }
        elderly.erasePersonalData();

        conversationIds.forEach(conversationContextService::clear);
        if (member != null) {
            authService.logout(member.getId());
        }
        log.info("Elderly erased: elderlyId={}, conversations={}, metricsKept={}",
                elderlyId, conversationIds.size(), keepMetrics);
    }

    /** 원문(메시지)은 지우고 대화 행은 시각·유형만 남긴 채 숨긴다. */
    private List<Long> eraseConversations(Long elderlyId) {
        List<Long> conversationIds = conversationRepository.findIdsByElderlyId(elderlyId);
        if (!conversationIds.isEmpty()) {
            messageRepository.deleteByConversationIdIn(conversationIds);
            conversationRepository.softDeleteAllByElderlyId(elderlyId);
        }
        return conversationIds;
    }
}
