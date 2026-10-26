package com.piuda.wilson.domain.member.service;

import com.piuda.wilson.domain.auth.service.AuthService;
import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.member.dto.ChangePasswordRequest;
import com.piuda.wilson.domain.member.dto.MemberResponse;
import com.piuda.wilson.domain.member.dto.UpdateMemberRequest;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.repository.MemberRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 회원 본인의 계정 관리 (api-spec 4장).
 *
 * <p>노인 회원은 조회만 할 수 있다. 노인 정보는 보호자가 노인 API(5-4, 5-5)로 관리한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final MemberRepository memberRepository;
    private final CaregiverRepository caregiverRepository;
    private final CareRelationshipRepository careRelationshipRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final ElderlyRepository elderlyRepository;

    @Transactional(readOnly = true)
    public MemberResponse getMe(Long memberId) {
        Member member = findMember(memberId);
        if (member.isCaregiver()) {
            return MemberResponse.from(member);
        }
        Long elderlyId = elderlyRepository.findByMemberIdAndDeletedFalse(memberId).map(Elderly::getId).orElse(null);
        return MemberResponse.of(member, elderlyId);
    }

    @Transactional
    public MemberResponse updateMe(Long memberId, UpdateMemberRequest request) {
        Member member = findCaregiverMember(memberId);
        member.updateProfile(request.name(), request.phone());
        return MemberResponse.from(member);
    }

    @Transactional
    public void changePassword(Long memberId, ChangePasswordRequest request) {
        Member member = findCaregiverMember(memberId);
        if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), member.getPassword())) {
            throw new CustomException(ErrorCode.SAME_PASSWORD);
        }
        member.changePassword(passwordEncoder.encode(request.newPassword()));
        log.info("Password changed: memberId={}", memberId);
    }

    /**
     * 다른 보호자가 있는 노인의 주보호자는 먼저 주보호자를 넘겨야 한다 (노인이 관리자 없이 남는 것 방지).
     * 탈퇴한 보호자에게만 연결돼 있던 노인은 연결만 끊기고 보호자 없는 상태가 된다.
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = findCaregiverMember(memberId);
        caregiverRepository.findByMemberId(memberId).ifPresent(caregiver -> {
            validateNoOwnershipToTransfer(caregiver.getId());
            markElderliesLeftAlone(caregiver.getId());
            careRelationshipRepository.deleteByCaregiverId(caregiver.getId());
        });
        member.withdraw();
        authService.logout(memberId);
        log.info("Member withdrawn: memberId={}", memberId);
    }

    private void validateNoOwnershipToTransfer(Long caregiverId) {
        boolean mustTransfer = careRelationshipRepository.findByCaregiverId(caregiverId).stream()
                .filter(CareRelationship::isOwner)
                .anyMatch(owned -> careRelationshipRepository.countByElderlyId(owned.getElderly().getId()) > 1);
        if (mustTransfer) {
            throw new CustomException(ErrorCode.OWNER_TRANSFER_REQUIRED);
        }
    }

    /** 이 보호자가 유일한 보호자였던 노인은 보호자 없는 상태가 된다 (30일 유예 후 파기). */
    private void markElderliesLeftAlone(Long caregiverId) {
        LocalDateTime now = LocalDateTime.now();
        careRelationshipRepository.findByCaregiverId(caregiverId).stream()
                .filter(relationship -> careRelationshipRepository.countByElderlyId(relationship.getElderly().getId()) == 1)
                .forEach(relationship -> {
                    relationship.getElderly().markOrphaned(now);
                    log.info("Elderly left without caregiver: elderlyId={}", relationship.getElderly().getId());
                });
    }

    private Member findMember(Long memberId) {
        return memberRepository.findByIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Member findCaregiverMember(Long memberId) {
        Member member = findMember(memberId);
        if (!member.isCaregiver()) {
            throw new CustomException(ErrorCode.NO_PERMISSION);
        }
        return member;
    }
}
