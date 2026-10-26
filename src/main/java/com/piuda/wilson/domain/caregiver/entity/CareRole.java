package com.piuda.wilson.domain.caregiver.entity;

/** 노인에 대한 보호자 역할. 노인마다 OWNER는 최대 1명이다 (api-spec 5장 권한 표). */
public enum CareRole {
    /** 주보호자: 노인 삭제, 보호자 초대·내보내기, 주보호자 넘기기, 대리 동의 */
    OWNER,
    /** 초대 코드로 들어온 보호자: 조회·수정·일정 관리 */
    MEMBER
}
