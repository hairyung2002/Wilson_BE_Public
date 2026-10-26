package com.piuda.wilson.domain.conversation.service;

import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.conversation.dto.ConversationDetailResponse;
import com.piuda.wilson.domain.conversation.dto.ConversationSummaryResponse;
import com.piuda.wilson.domain.conversation.dto.FinishConversationResponse;
import com.piuda.wilson.domain.conversation.dto.StartConversationRequest;
import com.piuda.wilson.domain.conversation.dto.StartConversationResponse;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationOrigin;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.ConversationState;
import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.conversation.repository.MessageRepository;
import com.piuda.wilson.domain.elderly.entity.ConsentType;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.elderly.service.ConsentService;
import com.piuda.wilson.global.common.response.PageResponse;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final List<ConversationState> CLOSED_STATES =
            List.of(ConversationState.FINISHED, ConversationState.FAILED);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ElderlyRepository elderlyRepository;
    private final CaregiverRepository caregiverRepository;
    private final CareRelationshipRepository careRelationshipRepository;
    private final ConversationContextService conversationContextService;
    private final ConsentService consentService;
    private final ConversationEndedAnnouncer conversationEndedAnnouncer;

    @Transactional
    public StartConversationResponse start(Long memberId, StartConversationRequest request) {
        return start(memberId, request.conversationType(), ConversationOrigin.USER);
    }

    /** 일정에 따라 AI가 먼저 말을 건 선제 대화를 만든다 (api-spec 6-2). */
    @Transactional
    public StartConversationResponse startProactive(Long memberId, ConversationType type) {
        return start(memberId, type, ConversationOrigin.PROACTIVE);
    }

    private StartConversationResponse start(Long memberId, ConversationType type, ConversationOrigin origin) {
        Elderly elderly = findElderlyByMember(memberId);
        LocalDateTime now = LocalDateTime.now();

        finishDanglingConversations(elderly, now);

        Conversation conversation = conversationRepository.save(Conversation.builder()
                .elderly(elderly)
                .channel(ConversationChannel.VOICE)
                .type(type)
                .origin(origin)
                .startedAt(now)
                .build());

        log.info("Conversation started: conversationId={}, elderlyId={}, origin={}",
                conversation.getId(), elderly.getId(), origin);
        return StartConversationResponse.from(conversation);
    }

    @Transactional
    public FinishConversationResponse finish(Long memberId, Long conversationId) {
        Elderly elderly = findElderlyByMember(memberId);
        Conversation conversation = findConversation(conversationId);
        if (!conversation.isOwnedBy(elderly.getId())) {
            throw new CustomException(ErrorCode.NO_PERMISSION);
        }
        if (conversation.isFinished()) {
            throw new CustomException(ErrorCode.CONVERSATION_ALREADY_FINISHED);
        }

        conversation.finish(LocalDateTime.now());
        conversationContextService.clear(conversationId);
        conversationEndedAnnouncer.announce(conversation);
        log.info("Conversation finished: conversationId={}", conversationId);
        return FinishConversationResponse.from(conversation);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationSummaryResponse> getConversations(Long memberId, Long elderlyId,
                                                                     LocalDate date, Pageable pageable) {
        elderlyRepository.findByIdAndDeletedFalse(elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ELDERLY_NOT_FOUND));
        validateCaregiverAccess(memberId, elderlyId);

        Page<Conversation> page = date == null
                ? conversationRepository.findByElderlyIdAndDeletedFalseOrderByStartedAtDescIdDesc(elderlyId, pageable)
                : conversationRepository.findPageByElderlyIdAndStartedAtRange(
                        elderlyId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), pageable);
        return PageResponse.from(page.map(ConversationSummaryResponse::from));
    }

    /** 대화 원문은 노인(또는 대리인)이 원문 공유에 동의했을 때만 보호자에게 보여준다 (decisions.md B1). */
    @Transactional(readOnly = true)
    public ConversationDetailResponse getConversation(Long memberId, Long conversationId) {
        Conversation conversation = findCaregiverAccessibleConversation(memberId, conversationId);
        if (!consentService.isAgreed(conversation.getElderly().getId(), ConsentType.TRANSCRIPT_SHARING)) {
            return ConversationDetailResponse.withoutTranscript(conversation);
        }
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
        return ConversationDetailResponse.withTranscript(conversation, messages);
    }

    @Transactional
    public void delete(Long memberId, Long conversationId) {
        Conversation conversation = findCaregiverAccessibleConversation(memberId, conversationId);
        conversation.delete();
        log.info("Conversation deleted: conversationId={}", conversationId);
    }

    private void finishDanglingConversations(Elderly elderly, LocalDateTime now) {
        List<Conversation> dangling =
                conversationRepository.findByElderlyIdAndStateNotInAndDeletedFalse(elderly.getId(), CLOSED_STATES);
        dangling.forEach(conversation -> {
            conversation.finish(now);
            conversationContextService.clear(conversation.getId());
            conversationEndedAnnouncer.announce(conversation);
            log.info("Dangling conversation auto-finished: conversationId={}", conversation.getId());
        });
    }

    private Elderly findElderlyByMember(Long memberId) {
        return elderlyRepository.findByMemberIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.ELDERLY_NOT_FOUND));
    }

    private Conversation findConversation(Long conversationId) {
        return conversationRepository.findByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    private Conversation findCaregiverAccessibleConversation(Long memberId, Long conversationId) {
        Conversation conversation = findConversation(conversationId);
        if (conversation.getElderly().isDeleted()) {
            throw new CustomException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        validateCaregiverAccess(memberId, conversation.getElderly().getId());
        return conversation;
    }

    private void validateCaregiverAccess(Long memberId, Long elderlyId) {
        Caregiver caregiver = caregiverRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAREGIVER_NOT_FOUND));
        if (!careRelationshipRepository.existsByCaregiverIdAndElderlyId(caregiver.getId(), elderlyId)) {
            throw new CustomException(ErrorCode.NO_PERMISSION);
        }
    }
}
