package com.piuda.wilson.domain.analysis.entity;

import com.piuda.wilson.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 턴 단위 인지지표. AI 서버가 Kafka로 보낸 값을 그대로 보관한다.
 *
 * <p>노인 PK가 아닌 가명 식별자(subjectId)로 쌓는다. 탈퇴·동의 철회 시 노인 개인정보와의 연결만 끊고
 * 수치는 연구용으로 남길 수 있게 하기 위함이다 (AI 레포 docs/research-retention.md).
 * 대화와는 turnId로 이어진다 (message.turn_id).</p>
 */
@Getter
@Entity
@Table(name = "cognitive_metric")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CognitiveMetric extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 턴당 지표는 하나다. Kafka 중복 수신은 이 값으로 걸러낸다. */
    @Column(name = "turn_id", nullable = false, unique = true, length = 36)
    private String turnId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    /** 발화가 너무 짧거나 잡음뿐이면 false이고, 이때 metrics는 비어 있다. */
    @Column(nullable = false)
    private boolean analyzable;

    @Embedded
    private SpeechMetrics metrics;

    /** 스키마 미확정(TBD) 지표. AI가 보낸 JSON 원문을 그대로 둔다. */
    @Column(columnDefinition = "text")
    private String neural;

    /** 스키마 미확정(TBD) 지표. AI가 보낸 JSON 원문을 그대로 둔다. */
    @Column(columnDefinition = "text")
    private String emotion;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @Builder
    private CognitiveMetric(String turnId, UUID subjectId, boolean analyzable, SpeechMetrics metrics,
                            String neural, String emotion, Instant analyzedAt) {
        this.turnId = turnId;
        this.subjectId = subjectId;
        this.analyzable = analyzable;
        this.metrics = metrics;
        this.neural = neural;
        this.emotion = emotion;
        this.analyzedAt = analyzedAt;
    }
}
