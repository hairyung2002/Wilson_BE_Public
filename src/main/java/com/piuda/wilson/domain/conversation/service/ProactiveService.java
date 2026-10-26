package com.piuda.wilson.domain.conversation.service;

import com.piuda.wilson.domain.conversation.dto.StartConversationResponse;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.ProactiveStatus;
import com.piuda.wilson.domain.conversation.entity.ProactiveTrigger;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.conversation.repository.ProactiveTriggerRepository;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.entity.PushToken;
import com.piuda.wilson.domain.notification.repository.PushTokenRepository;
import com.piuda.wilson.domain.notification.service.NotificationService;
import com.piuda.wilson.domain.schedule.entity.Schedule;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import com.piuda.wilson.infrastructure.prompt.ProactiveGreetingTemplate;
import com.piuda.wilson.infrastructure.push.PushMessage;
import com.piuda.wilson.infrastructure.push.PushResult;
import com.piuda.wilson.infrastructure.push.PushSender;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선제 대화 (api-spec 6-2, 결정 C4·C5).
 *
 * <p>일정 시각에 첫 안부 문장을 만들어 노인 기기에 알림으로 보내고(결정 J4), 앱이 트리거로 대화를 만든다.
 * 10분 동안 노인이 말하지 않으면 한 번 더 보내고, 다시 10분이 지나면 보호자에게 무응답을 알린다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProactiveService {

    private static final Duration WAIT = Duration.ofMinutes(10);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String PUSH_TITLE = "윌슨";

    private final ProactiveTriggerRepository proactiveTriggerRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final PushTokenRepository pushTokenRepository;
    private final PushSender pushSender;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public void trigger(Schedule schedule, LocalDateTime scheduledAt) {
        String greeting = ProactiveGreetingTemplate.render(schedule.getType().toConversationType(),
                schedule.getLabel(), schedule.getElderly().addressTerm(), scheduledAt.toLocalTime());
        ProactiveTrigger trigger = ProactiveTrigger.create(schedule, scheduledAt, greeting);
        if (!push(trigger)) {
            trigger.markUndeliverable();
        }
        proactiveTriggerRepository.save(trigger);
        log.info("Proactive conversation triggered: elderlyId={}, triggerId={}, status={}",
                schedule.getElderly().getId(), trigger.getId(), trigger.getStatus());
    }

    /** 재푸시로 같은 트리거가 다시 오면 이미 만든 대화를 그대로 돌려준다. */
    @Transactional
    public StartConversationResponse start(Long memberId, String triggerId) {
        ProactiveTrigger trigger = proactiveTriggerRepository.findById(triggerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROACTIVE_TRIGGER_NOT_FOUND));
        if (!trigger.isFor(memberId)) {
            throw new CustomException(ErrorCode.NO_PERMISSION);
        }
        if (trigger.hasConversation()) {
            return conversationRepository.findByIdAndDeletedFalse(trigger.getConversationId())
                    .filter(conversation -> !conversation.isFinished())
                    .map(StartConversationResponse::from)
                    .orElseThrow(() -> new CustomException(ErrorCode.PROACTIVE_TRIGGER_UNAVAILABLE));
        }
        if (!trigger.isUsable(LocalDateTime.now(clock))) {
            throw new CustomException(ErrorCode.PROACTIVE_TRIGGER_UNAVAILABLE);
        }
        StartConversationResponse response = conversationService.startProactive(memberId,
                trigger.getConversationType());
        trigger.attachConversation(response.conversationId());
        return response;
    }

    /** 노인이 선제 대화에서 말하면 응답한 것으로 본다. 일반 대화면 아무 일도 없다. */
    @Transactional
    public void markResponded(Long conversationId) {
        proactiveTriggerRepository.findByConversationId(conversationId).ifPresent(ProactiveTrigger::markResponded);
    }

    @Transactional
    public void followUp() {
        LocalDateTime now = LocalDateTime.now(clock);
        proactiveTriggerRepository.findByStatusAndRetriedAtLessThanEqual(ProactiveStatus.RETRIED, now.minus(WAIT))
                .forEach(this::markMissed);
        proactiveTriggerRepository.findByStatusAndSentAtLessThanEqual(ProactiveStatus.SENT, now.minus(WAIT))
                .forEach(trigger -> {
                    push(trigger);
                    trigger.markRetried(now);
                });
    }

    private void markMissed(ProactiveTrigger trigger) {
        trigger.markMissed();
        notificationService.notifyCaregivers(trigger.getElderly().getId(), NotificationType.PROACTIVE_NO_RESPONSE,
                trigger.getElderly().getName() + "님 선제 대화",
                trigger.getScheduledAt().format(HH_MM) + " " + subject(trigger) + "에 응답이 없으셨어요");
        log.info("Proactive conversation missed: elderlyId={}, triggerId={}",
                trigger.getElderly().getId(), trigger.getId());
    }

    /** 노인 기기로 데이터 푸시를 보낸다. 기기가 없거나 토큰이 무효면 false. */
    private boolean push(ProactiveTrigger trigger) {
        Member member = trigger.getElderly().getMember();
        Optional<PushToken> pushToken = member == null ? Optional.empty()
                : pushTokenRepository.findByMemberId(member.getId());
        if (pushToken.isEmpty()) {
            return false;
        }
        PushResult result = pushSender.send(new PushMessage(pushToken.get().getToken(), PUSH_TITLE,
                trigger.getGreeting(), data(trigger)));
        if (result == PushResult.INVALID_TOKEN) {
            pushTokenRepository.delete(pushToken.get());
            return false;
        }
        return true;
    }

    private static Map<String, String> data(ProactiveTrigger trigger) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "PROACTIVE");
        data.put("triggerId", trigger.getId());
        data.put("conversationType", trigger.getConversationType().name());
        data.put("greeting", trigger.getGreeting());
        return data;
    }

    private static String subject(ProactiveTrigger trigger) {
        if (trigger.getLabel() != null) {
            return trigger.getLabel() + " 안부";
        }
        ConversationType type = trigger.getConversationType();
        return switch (type) {
            case MEDICATION -> "복약 안부";
            case MEAL -> "식사 안부";
            case WALK -> "산책 안부";
            default -> "안부 인사";
        };
    }
}
