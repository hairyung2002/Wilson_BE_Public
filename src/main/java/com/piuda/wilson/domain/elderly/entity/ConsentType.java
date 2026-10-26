package com.piuda.wilson.domain.elderly.entity;

/** 노인 동의 종류 (api-spec 5-12). */
public enum ConsentType {
    /** 음성·대화·건강 관련 민감정보 처리. 필수이며 철회하려면 노인을 삭제한다 */
    SENSITIVE_INFO,
    /** 삭제·철회 후에도 인지지표를 가명으로 연구 보관 */
    RESEARCH_RETENTION,
    /** 보호자에게 대화 원문 공개. 없으면 보호자는 요약·지표만 본다 */
    TRANSCRIPT_SHARING
}
