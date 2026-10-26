package com.piuda.wilson.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 한 턴의 발화에서 AI 서버가 계산한 원시 인지지표 (AI 레포 docs/erd.md COGNITIVE_METRIC).
 * 계산하지 못한 지표는 null이다. cut-off·이상 판정은 BE가 이 값을 누적해 따로 한다.
 */
@Embeddable
public record SpeechMetrics(
        @Column(name = "morpheme_token_count") Integer morphemeTokenCount,
        @Column(name = "utterance_count") Integer utteranceCount,
        @Column(name = "filler_ratio") Double fillerRatio,
        @Column(name = "empty_speech_ratio") Double emptySpeechRatio,
        @Column(name = "content_info_unit_ratio") Double contentInfoUnitRatio,
        @Column(name = "words_per_utterance") Double wordsPerUtterance,
        @Column(name = "subsequent_onset_latency_ms") Double subsequentOnsetLatencyMs,
        @Column(name = "silent_pause_ratio") Double silentPauseRatio,
        @Column(name = "jitter") Double jitter,
        @Column(name = "shimmer") Double shimmer,
        @Column(name = "cpp") Double cpp
) {
}
