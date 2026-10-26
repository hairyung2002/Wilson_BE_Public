package com.piuda.wilson.domain.emergency.service;

import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.service.ElderlyAccessService;
import com.piuda.wilson.domain.emergency.dto.EmergencyResponse;
import com.piuda.wilson.domain.emergency.entity.Emergency;
import com.piuda.wilson.domain.emergency.repository.EmergencyRepository;
import com.piuda.wilson.domain.member.repository.MemberRepository;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.service.NotificationService;
import com.piuda.wilson.global.common.response.PageResponse;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 응급 (api-spec 15장, 결정 G2). AI가 감지하면 기록하고 연결된 보호자 전원에게 즉시 알린다
 * (EMERGENCY 알림은 설정·방해 금지 시간과 무관, 14장). 119 자동 연결은 하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyService {

    static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(10);

    private final EmergencyRepository emergencyRepository;
    private final ConversationRepository conversationRepository;
    private final MemberRepository memberRepository;
    private final ElderlyAccessService elderlyAccessService;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public void report(Long conversationId, String turnId, EmergencyCategory category) {
        Elderly elderly = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONVERSATION_NOT_FOUND))
                .getElderly();
        LocalDateTime now = LocalDateTime.now(clock);
        boolean duplicate = emergencyRepository.existsByElderlyIdAndCategoryAndNotifiedTrueAndDetectedAtGreaterThanEqual(
                elderly.getId(), category, now.minus(DUPLICATE_WINDOW));
        emergencyRepository.save(Emergency.builder().elderly(elderly).conversationId(conversationId).turnId(turnId)
                .category(category).detectedAt(now).notified(!duplicate).build());
        log.warn("Emergency detected: elderlyId={}, conversationId={}, turnId={}, category={}, notified={}",
                elderly.getId(), conversationId, turnId, category, !duplicate);
        if (!duplicate) {
            notificationService.notifyCaregivers(elderly.getId(), NotificationType.EMERGENCY,
                    elderly.getName() + "님 확인이 필요해요", body(elderly.getName(), category));
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<EmergencyResponse> getEmergencies(Long memberId, Long elderlyId, Pageable pageable) {
        elderlyAccessService.requireCaregiver(memberId, elderlyId);
        return PageResponse.from(emergencyRepository.findByElderlyIdOrderByDetectedAtDesc(elderlyId, pageable)
                .map(EmergencyResponse::from));
    }

    @Transactional
    public void acknowledge(Long memberId, Long emergencyId) {
        Emergency emergency = emergencyRepository.findById(emergencyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND));
        elderlyAccessService.requireCaregiver(memberId, emergency.getElderly().getId());
        if (emergency.isAcknowledged()) {
            return;
        }
        emergency.acknowledge(memberRepository.getReferenceById(memberId), LocalDateTime.now(clock));
        log.info("Emergency acknowledged: emergencyId={}, memberId={}", emergencyId, memberId);
    }

    /** 대화 원문은 넣지 않는다 (결정 E4). 의료 판단 표현도 쓰지 않는다. */
    private static String body(String name, EmergencyCategory category) {
        String what = switch (category) {
            case FALL -> "넘어지셨다는";
            case PAIN -> "아프시다는";
            case BREATHING -> "숨쉬기 힘드시다는";
            case HELP_REQUEST -> "도움을 청하시는";
            case OTHER -> "걱정되는";
        };
        return name + "님 대화에서 " + what + " 말씀이 있었어요. 바로 연락해 보세요";
    }
}
