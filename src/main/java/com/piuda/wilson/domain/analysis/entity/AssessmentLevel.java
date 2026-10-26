package com.piuda.wilson.domain.analysis.entity;

/**
 * 판정 상태와 보호자 표시 문구 (api-spec 12-3·12-4, 결정 D4·D5).
 * 값(LOW 등)은 화면에 그대로 보이지 않고 message만 보인다. 판정 이력에는 LOW·MEDIUM·HIGH만 저장된다.
 */
public enum AssessmentLevel {

    COLLECTING("평소 대화 모습을 알아가는 중이에요 (약 2주)"),
    LOW("평소와 비슷한 대화를 나누고 계세요"),
    MEDIUM("최근 며칠 평소와 조금 다른 대화 모습이 보여요"),
    HIGH("최근 며칠 평소와 다른 대화 모습이 이어지고 있어요. 어르신과 이야기 나눠 보시고, 걱정되시면 전문가와 상담해 보세요");

    private final String message;

    AssessmentLevel(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    /** 판정된 지표 0개 LOW · 1개 MEDIUM · 2개 이상 HIGH */
    public static AssessmentLevel ofFlaggedCount(long flaggedCount) {
        if (flaggedCount >= 2) {
            return HIGH;
        }
        return flaggedCount == 1 ? MEDIUM : LOW;
    }

    public boolean isHigherThan(AssessmentLevel other) {
        return compareTo(other) > 0;
    }
}
