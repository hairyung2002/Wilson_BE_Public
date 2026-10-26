package com.piuda.wilson.domain.elderly.service;

/** 노인 데이터에 접근한 사람 (api-spec 5장 권한 표). */
public enum AccessActor {
    OWNER,
    MEMBER,
    /** 노인 본인 (노인 회원 토큰) */
    SELF
}
