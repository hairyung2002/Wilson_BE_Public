package com.piuda.wilson.domain.analysis.entity;

import java.util.function.Function;

/**
 * 변화 판정에 쓰는 지표 (api-spec 12-1, 결정 D1). 11개 지표는 모두 저장하고 판정은 이 4개로 시작한다.
 * label은 보호자 화면에 그대로 보이므로 의료 표현을 쓰지 않는다 (결정 D5).
 */
public enum JudgedMetric {

    SUBSEQUENT_ONSET_LATENCY("subsequent_onset_latency_ms", "대답까지 걸리는 시간", "ms", true,
            SpeechMetrics::subsequentOnsetLatencyMs),
    FILLER_RATIO("filler_ratio", "\"음…\" 같은 머뭇거림", "ratio", true, SpeechMetrics::fillerRatio),
    CONTENT_INFO_UNIT_RATIO("content_info_unit_ratio", "대화에 담긴 내용", "ratio", false,
            SpeechMetrics::contentInfoUnitRatio),
    WORDS_PER_UTTERANCE("words_per_utterance", "한 번에 말하는 길이", "words", false,
            SpeechMetrics::wordsPerUtterance);

    private final String code;
    private final String label;
    private final String unit;
    private final boolean worseWhenHigher;
    private final Function<SpeechMetrics, Double> extractor;

    JudgedMetric(String code, String label, String unit, boolean worseWhenHigher,
                 Function<SpeechMetrics, Double> extractor) {
        this.code = code;
        this.label = label;
        this.unit = unit;
        this.worseWhenHigher = worseWhenHigher;
        this.extractor = extractor;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public boolean isWorseWhenHigher() {
        return worseWhenHigher;
    }

    /** 이 턴에서 계산하지 못한 지표면 null. */
    public Double valueOf(SpeechMetrics metrics) {
        return metrics == null ? null : extractor.apply(metrics);
    }
}
