package com.piuda.wilson.domain.member.dto;

import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import java.time.LocalDateTime;

/** elderlyId는 노인 회원일 때만 있다 (노인 기기가 일정·설정 API를 부를 때 사용, api-spec 4-1). */
public record MemberResponse(Long id, String name, String email, String phone, MemberType memberType,
                             LocalDateTime createdAt, Long elderlyId) {

    public static MemberResponse from(Member member) {
        return of(member, null);
    }

    public static MemberResponse of(Member member, Long elderlyId) {
        return new MemberResponse(
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getPhone(),
                member.getMemberType(),
                member.getCreatedAt(),
                elderlyId
        );
    }
}
