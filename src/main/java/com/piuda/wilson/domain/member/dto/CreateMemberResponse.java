package com.piuda.wilson.domain.member.dto;

import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;

public record CreateMemberResponse(Long id, String email, String name, MemberType memberType) {

    public static CreateMemberResponse from(Member member) {
        return new CreateMemberResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getMemberType()
        );
    }
}
