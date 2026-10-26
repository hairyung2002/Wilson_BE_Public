package com.piuda.wilson.domain.caregiver.service;

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
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노인별 초대 코드로 보호자를 연결한다 (api-spec 5-7·5-8).
 *
 * <p>노인 ID만으로 연결하면 남의 노인과 연결해 대화를 볼 수 있으므로, 주보호자(또는 주보호자가 없을 때 노인 본인)가
 * 발급한 코드를 가진 사람만 연결할 수 있다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareInvitationService {

    private static final Duration INVITE_CODE_TTL = Duration.ofHours(24);
    private static final int ISSUE_MAX_ATTEMPTS = 3;
    private static final Duration JOIN_WINDOW = Duration.ofMinutes(10);
    private static final int JOIN_MAX_ATTEMPTS = 10;

    private final CaregiverRepository caregiverRepository;
    private final ElderlyRepository elderlyRepository;
    private final CareRelationshipRepository careRelationshipRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final LoginAttemptRepository loginAttemptRepository;

    @Transactional(readOnly = true)
    public InviteCodeResponse issueInviteCode(Long memberId, Long elderlyId) {
        Elderly elderly = elderlyRepository.findByIdAndDeletedFalse(elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ELDERLY_NOT_FOUND));
        validateInviter(memberId, elderly);

        for (int attempt = 0; attempt < ISSUE_MAX_ATTEMPTS; attempt++) {
            String code = inviteCodeGenerator.generate();
            if (inviteCodeRepository.issue(code, elderlyId, INVITE_CODE_TTL)) {
                log.info("Invite code issued: elderlyId={}", elderlyId);
                return new InviteCodeResponse(code, LocalDateTime.now().plus(INVITE_CODE_TTL));
            }
        }
        log.warn("Invite code issue failed after {} attempts: elderlyId={}", ISSUE_MAX_ATTEMPTS, elderlyId);
        throw new CustomException(ErrorCode.INVITE_CODE_ISSUE_FAILED);
    }

    /** 코드 무작위 대입을 막기 위해 코드를 확인하기 전에 회원별 시도 횟수를 센다. 코드는 연결에 성공했을 때만 지운다. */
    @Transactional
    public JoinCareRelationshipResponse join(Long memberId, JoinCareRelationshipRequest request) {
        long attempts = loginAttemptRepository.incrementInviteCode(memberId, JOIN_WINDOW);
        if (attempts > JOIN_MAX_ATTEMPTS) {
            log.warn("Invite code join rate limited: memberId={}, attempts={}", memberId, attempts);
            throw new CustomException(ErrorCode.TOO_MANY_INVITE_CODE_ATTEMPTS);
        }
        Caregiver caregiver = caregiverRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAREGIVER_NOT_FOUND));
        String code = request.inviteCode().trim().toUpperCase(Locale.ROOT);
        Elderly elderly = findInvitedElderly(code);
        if (careRelationshipRepository.existsByCaregiverIdAndElderlyId(caregiver.getId(), elderly.getId())) {
            throw new CustomException(ErrorCode.DUPLICATE_CARE_RELATIONSHIP);
        }

        CareRole role = careRelationshipRepository.existsByElderlyIdAndRole(elderly.getId(), CareRole.OWNER)
                ? CareRole.MEMBER : CareRole.OWNER;
        CareRelationship careRelationship = careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(caregiver)
                .elderly(elderly)
                .relation(request.relation())
                .role(role)
                .build());
        elderly.clearOrphaned();
        inviteCodeRepository.delete(code, elderly.getId());

        log.info("Caregiver joined by invite code: elderlyId={}, caregiverId={}, role={}",
                elderly.getId(), caregiver.getId(), role);
        return JoinCareRelationshipResponse.from(careRelationship);
    }

    /** 주보호자, 또는 주보호자가 없을 때의 노인 본인만 초대할 수 있다. */
    private void validateInviter(Long memberId, Elderly elderly) {
        Optional<Caregiver> caregiver = caregiverRepository.findByMemberId(memberId);
        if (caregiver.isPresent()) {
            boolean owner = careRelationshipRepository
                    .findByCaregiverIdAndElderlyId(caregiver.get().getId(), elderly.getId())
                    .map(CareRelationship::isOwner)
                    .orElse(false);
            if (!owner) {
                throw new CustomException(ErrorCode.NO_PERMISSION);
            }
            return;
        }
        boolean self = elderly.getMember() != null && elderly.getMember().getId().equals(memberId);
        if (!self || careRelationshipRepository.existsByElderlyIdAndRole(elderly.getId(), CareRole.OWNER)) {
            throw new CustomException(ErrorCode.NO_PERMISSION);
        }
    }

    private Elderly findInvitedElderly(String code) {
        Long elderlyId = inviteCodeRepository.findElderlyId(code)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));
        return elderlyRepository.findByIdAndDeletedFalse(elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));
    }
}
