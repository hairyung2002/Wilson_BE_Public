package com.piuda.wilson.domain.elderly.service;

import com.piuda.wilson.domain.auth.repository.DeviceCodeRepository;
import com.piuda.wilson.domain.auth.service.DeviceCodeGenerator;
import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.elderly.dto.CreateElderlyRequest;
import com.piuda.wilson.domain.elderly.dto.CreateElderlyResponse;
import com.piuda.wilson.domain.elderly.dto.DeviceCodeResponse;
import com.piuda.wilson.domain.elderly.dto.ElderlyDetailResponse;
import com.piuda.wilson.domain.elderly.dto.ElderlySummaryResponse;
import com.piuda.wilson.domain.elderly.dto.UpdateElderlyRequest;
import com.piuda.wilson.domain.elderly.entity.ConsentGrantor;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElderlyService {

    private static final Duration DEVICE_CODE_TTL = Duration.ofMinutes(10);
    private static final int DEVICE_CODE_MAX_ATTEMPTS = 3;

    private final ElderlyRepository elderlyRepository;
    private final CaregiverRepository caregiverRepository;
    private final CareRelationshipRepository careRelationshipRepository;
    private final DeviceCodeRepository deviceCodeRepository;
    private final DeviceCodeGenerator deviceCodeGenerator;
    private final ConsentService consentService;
    private final ElderlyDeletionService elderlyDeletionService;

    @Transactional
    public CreateElderlyResponse register(Long memberId, CreateElderlyRequest request) {
        Caregiver caregiver = findCaregiver(memberId);

        Elderly elderly = elderlyRepository.save(Elderly.builder()
                .name(request.name())
                .birthDate(request.birth())
                .gender(request.gender())
                .build());

        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(caregiver)
                .elderly(elderly)
                .relation(request.relationship())
                .role(CareRole.OWNER)
                .build());
        consentService.recordInitial(elderly, request.consents(), memberId, ConsentGrantor.OWNER,
                request.legalBasis());

        log.info("Elderly registered: elderlyId={}, caregiverId={}", elderly.getId(), caregiver.getId());
        return new CreateElderlyResponse(elderly.getId());
    }

    @Transactional(readOnly = true)
    public List<ElderlySummaryResponse> getElderlies(Long memberId) {
        Caregiver caregiver = findCaregiver(memberId);
        LocalDate today = LocalDate.now();
        return careRelationshipRepository.findAllWithActiveElderlyByCaregiverId(caregiver.getId()).stream()
                .map(careRelationship -> ElderlySummaryResponse.of(careRelationship, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public ElderlyDetailResponse getElderly(Long memberId, Long elderlyId) {
        return ElderlyDetailResponse.from(findAuthorizedRelationship(memberId, elderlyId));
    }

    @Transactional
    public ElderlyDetailResponse update(Long memberId, Long elderlyId, UpdateElderlyRequest request) {
        CareRelationship careRelationship = findAuthorizedRelationship(memberId, elderlyId);

        careRelationship.getElderly().updateProfile(request.name(), request.birth());
        if (request.relationship() != null) {
            careRelationship.changeRelation(request.relationship());
        }

        return ElderlyDetailResponse.from(careRelationship);
    }

    /** 주보호자만 삭제할 수 있다. 개인정보·원문 파기 정책은 {@link ElderlyDeletionService}를 따른다. */
    @Transactional
    public void delete(Long memberId, Long elderlyId) {
        CareRelationship careRelationship = findAuthorizedRelationship(memberId, elderlyId);
        if (!careRelationship.isOwner()) {
            throw new CustomException(ErrorCode.NO_PERMISSION);
        }
        Long caregiverId = careRelationship.getCaregiver().getId();
        elderlyDeletionService.erase(elderlyId);
        log.info("Elderly deleted by owner: elderlyId={}, caregiverId={}", elderlyId, caregiverId);
    }

    @Transactional(readOnly = true)
    public DeviceCodeResponse issueDeviceCode(Long memberId, Long elderlyId) {
        findAuthorizedRelationship(memberId, elderlyId);

        for (int attempt = 0; attempt < DEVICE_CODE_MAX_ATTEMPTS; attempt++) {
            String code = deviceCodeGenerator.generate();
            if (deviceCodeRepository.saveIfAbsent(code, elderlyId, DEVICE_CODE_TTL)) {
                log.info("Device code issued: elderlyId={}", elderlyId);
                return new DeviceCodeResponse(code, LocalDateTime.now().plus(DEVICE_CODE_TTL));
            }
        }
        log.warn("Device code issue failed after {} attempts: elderlyId={}", DEVICE_CODE_MAX_ATTEMPTS, elderlyId);
        throw new CustomException(ErrorCode.DEVICE_CODE_ISSUE_FAILED);
    }

    private Caregiver findCaregiver(Long memberId) {
        return caregiverRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAREGIVER_NOT_FOUND));
    }

    private CareRelationship findAuthorizedRelationship(Long memberId, Long elderlyId) {
        Caregiver caregiver = findCaregiver(memberId);
        Elderly elderly = elderlyRepository.findByIdAndDeletedFalse(elderlyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ELDERLY_NOT_FOUND));
        return careRelationshipRepository.findByCaregiverIdAndElderlyId(caregiver.getId(), elderly.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.NO_PERMISSION));
    }
}
