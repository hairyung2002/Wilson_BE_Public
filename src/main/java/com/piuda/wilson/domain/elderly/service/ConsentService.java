package com.piuda.wilson.domain.elderly.service;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
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
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노인 동의 기록 (api-spec 5-12, decisions.md B4). 법률 자문 전까지는 기록만 남기고,
 * 원문 공개(B1)·연구 보관(B5) 판단에 최신 기록을 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final ElderlyRepository elderlyRepository;
    private final CaregiverRepository caregiverRepository;
    private final CareRelationshipRepository careRelationshipRepository;

    /** 노인 등록(대리, OWNER)·노인 본인 가입(SELF) 때 받은 동의를 저장한다. 민감정보 처리 동의가 없으면 등록할 수 없다. */
    @Transactional
    public void recordInitial(Elderly elderly, List<ConsentItem> items, Long grantorMemberId,
                              ConsentGrantor grantor, LegalBasis legalBasis) {
        LegalBasis basis = resolveLegalBasis(grantor, legalBasis);
        List<ConsentItem> consents = items == null ? List.of() : items;
        validateNoDuplicateType(consents);
        boolean sensitiveAgreed = consents.stream()
                .anyMatch(item -> item.type() == ConsentType.SENSITIVE_INFO && Boolean.TRUE.equals(item.agreed()));
        if (!sensitiveAgreed) {
            throw new CustomException(ErrorCode.REQUIRED_CONSENT_MISSING);
        }
        consents.forEach(item -> consentRepository.save(Consent.builder()
                .elderly(elderly)
                .type(item.type())
                .agreed(item.agreed())
                .version(item.version())
                .grantor(grantor)
                .grantedByMemberId(grantorMemberId)
                .legalBasis(basis)
                .build()));
    }

    /** 연결된 보호자와 노인 본인이 종류별 최신 동의를 본다. */
    @Transactional(readOnly = true)
    public List<ConsentResponse> getConsents(Long memberId, Long elderlyId) {
        Elderly elderly = findElderly(elderlyId);
        if (!isSelf(elderly, memberId)) {
            findRelationship(memberId, elderlyId);
        }
        Set<ConsentType> seen = EnumSet.noneOf(ConsentType.class);
        return consentRepository.findByElderlyIdOrderByIdDesc(elderlyId).stream()
                .filter(consent -> seen.add(consent.getType()))
                .map(ConsentResponse::from)
                .toList();
    }

    /** 노인 본인(SELF) 또는 주보호자(대리, 법적 근거 필수)만 바꾼다. 기록은 고치지 않고 새로 쌓는다. */
    @Transactional
    public void updateConsent(Long memberId, Long elderlyId, ConsentType type, UpdateConsentRequest request) {
        Elderly elderly = findElderly(elderlyId);
        ConsentGrantor grantor = resolveGrantor(memberId, elderly);
        LegalBasis basis = resolveLegalBasis(grantor, request.legalBasis());
        if (type == ConsentType.SENSITIVE_INFO && !request.agreed()) {
            throw new CustomException(ErrorCode.REQUIRED_CONSENT_NOT_REVOCABLE);
        }
        consentRepository.save(Consent.builder()
                .elderly(elderly)
                .type(type)
                .agreed(request.agreed())
                .version(request.version())
                .grantor(grantor)
                .grantedByMemberId(memberId)
                .legalBasis(basis)
                .build());
        log.info("Consent updated: elderlyId={}, type={}, agreed={}, grantor={}",
                elderlyId, type, request.agreed(), grantor);
    }

    @Transactional(readOnly = true)
    public boolean isAgreed(Long elderlyId, ConsentType type) {
        return consentRepository.findFirstByElderlyIdAndTypeOrderByIdDesc(elderlyId, type)
                .map(Consent::isAgreed)
                .orElse(false);
    }

    private ConsentGrantor resolveGrantor(Long memberId, Elderly elderly) {
        Optional<Caregiver> caregiver = caregiverRepository.findByMemberId(memberId);
        if (caregiver.isPresent()) {
            boolean owner = careRelationshipRepository
                    .findByCaregiverIdAndElderlyId(caregiver.get().getId(), elderly.getId())
                    .map(CareRelationship::isOwner)
                    .orElse(false);
            if (owner) {
                return ConsentGrantor.OWNER;
            }
        } else if (isSelf(elderly, memberId)) {
            return ConsentGrantor.SELF;
        }
        throw new CustomException(ErrorCode.NO_PERMISSION);
    }

    /** 본인 동의는 언제나 SELF, 대리 동의는 SELF가 아닌 법적 근거가 있어야 한다. */
    private static LegalBasis resolveLegalBasis(ConsentGrantor grantor, LegalBasis legalBasis) {
        if (grantor == ConsentGrantor.SELF) {
            return LegalBasis.SELF;
        }
        if (legalBasis == null || legalBasis == LegalBasis.SELF) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return legalBasis;
    }

    private static void validateNoDuplicateType(List<ConsentItem> items) {
        if (items.stream().map(ConsentItem::type).distinct().count() != items.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static boolean isSelf(Elderly elderly, Long memberId) {
        return elderly.getMember() != null && elderly.getMember().getId().equals(memberId);
    }

    private Elderly findElderly(Long elderlyId) {
        return elderlyRepository.findByIdAndDeletedFalse(elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ELDERLY_NOT_FOUND));
    }

    private CareRelationship findRelationship(Long memberId, Long elderlyId) {
        Caregiver caregiver = caregiverRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.NO_PERMISSION));
        return careRelationshipRepository.findByCaregiverIdAndElderlyId(caregiver.getId(), elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.NO_PERMISSION));
    }
}
