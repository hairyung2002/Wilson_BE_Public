package com.piuda.wilson.domain.elderly.entity;

/** 동의의 법적 근거. 대리 동의는 SELF가 아닌 값이어야 한다 (법률 자문 전 기록용). */
public enum LegalBasis {
    /** 노인 본인 동의 */
    SELF,
    /** 성년후견 */
    GUARDIANSHIP,
    /** 가족 위임 */
    FAMILY_DELEGATION,
    OTHER
}
