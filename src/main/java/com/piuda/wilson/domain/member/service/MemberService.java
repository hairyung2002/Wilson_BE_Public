package com.piuda.wilson.domain.member.service;

import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.elderly.entity.ConsentGrantor;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.entity.LegalBasis;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.elderly.service.ConsentService;
import com.piuda.wilson.domain.member.dto.CreateMemberRequest;
import com.piuda.wilson.domain.member.dto.CreateMemberResponse;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.member.repository.MemberRepository;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final ElderlyRepository elderlyRepository;
    private final CaregiverRepository caregiverRepository;
    private final PasswordEncoder passwordEncoder;
    private final ConsentService consentService;

    @Transactional
    public CreateMemberResponse create(CreateMemberRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        Member member = memberRepository.save(Member.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .memberType(request.memberType())
                .build());

        createProfile(member, request);

        return CreateMemberResponse.from(member);
    }

    /** 노인 본인 가입이면 본인 동의(SELF)를 함께 기록한다. */
    private void createProfile(Member member, CreateMemberRequest request) {
        if (member.getMemberType() == MemberType.ELDERLY) {
            Elderly elderly = elderlyRepository.save(Elderly.builder().member(member).name(member.getName()).build());
            consentService.recordInitial(elderly, request.consents(), member.getId(), ConsentGrantor.SELF,
                    LegalBasis.SELF);
        } else {
            caregiverRepository.save(Caregiver.builder().member(member).build());
        }
    }
}
