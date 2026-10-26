package com.piuda.wilson.domain.analysis.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.piuda.wilson.domain.analysis.entity.CognitiveMetric;
import com.piuda.wilson.domain.analysis.entity.SpeechMetrics;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class CognitiveMetricRepositoryTest {

    private static final UUID SUBJECT_ID = UUID.randomUUID();

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CognitiveMetricRepository cognitiveMetricRepository;

    private static CognitiveMetric metric(String turnId, SpeechMetrics metrics) {
        return CognitiveMetric.builder()
                .turnId(turnId)
                .subjectId(SUBJECT_ID)
                .analyzable(metrics != null)
                .metrics(metrics)
                .neural("{\"score\":0.3}")
                .analyzedAt(Instant.parse("2026-10-20T01:15:30Z"))
                .build();
    }

    @Test
    @DisplayName("턴 단위 인지지표를 저장하고, 턴 식별자로 이미 받았는지 확인한다")
    void saveAndExistsByTurnId() {
        // given
        SpeechMetrics metrics = new SpeechMetrics(42, 3, 0.12, 0.05, 0.4, 14.0, 820.0, 0.18, 0.011, 0.042, 12.3);

        // when
        CognitiveMetric saved = cognitiveMetricRepository.saveAndFlush(metric("turn-1", metrics));
        entityManager.clear();

        // then
        CognitiveMetric found = cognitiveMetricRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getSubjectId()).isEqualTo(SUBJECT_ID);
        assertThat(found.getMetrics()).isEqualTo(metrics);
        assertThat(found.getNeural()).isEqualTo("{\"score\":0.3}");
        assertThat(found.getAnalyzedAt()).isEqualTo(Instant.parse("2026-10-20T01:15:30Z"));
        assertThat(cognitiveMetricRepository.existsByTurnId("turn-1")).isTrue();
        assertThat(cognitiveMetricRepository.existsByTurnId("turn-2")).isFalse();
    }

    @Test
    @DisplayName("분석 불가 턴은 수치 없이 저장된다")
    void saveNotAnalyzable() {
        // when
        CognitiveMetric saved = cognitiveMetricRepository.saveAndFlush(metric("turn-1", null));
        entityManager.clear();

        // then
        CognitiveMetric found = cognitiveMetricRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.isAnalyzable()).isFalse();
        assertThat(found.getMetrics()).isNull();
    }

    @Test
    @DisplayName("가명 식별자로 그 노인의 지표를 모두 지운다 (연구 보관 동의가 없는 노인 삭제)")
    void deleteBySubjectId() {
        // given
        cognitiveMetricRepository.save(metric("turn-1", null));
        cognitiveMetricRepository.save(metric("turn-2", null));
        cognitiveMetricRepository.save(CognitiveMetric.builder().turnId("turn-3").subjectId(UUID.randomUUID())
                .analyzable(false).analyzedAt(Instant.now()).build());
        entityManager.flush();

        // when
        cognitiveMetricRepository.deleteBySubjectId(SUBJECT_ID);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(cognitiveMetricRepository.findAll()).extracting(CognitiveMetric::getTurnId).containsExactly("turn-3");
    }

    @Test
    @DisplayName("판정에 쓸 지표는 그 노인의 분석 가능한 지표만 기간으로 조회한다")
    void findAnalyzableInRange() {
        // given
        SpeechMetrics metrics = new SpeechMetrics(42, 3, 0.12, 0.05, 0.4, 14.0, 820.0, 0.18, 0.011, 0.042, 12.3);
        Instant dayStart = Instant.parse("2026-10-19T15:00:00Z");
        Instant dayEnd = Instant.parse("2026-10-20T15:00:00Z");
        cognitiveMetricRepository.save(CognitiveMetric.builder().turnId("before").subjectId(SUBJECT_ID)
                .analyzable(true).metrics(metrics).analyzedAt(dayStart.minusSeconds(1)).build());
        cognitiveMetricRepository.save(CognitiveMetric.builder().turnId("in").subjectId(SUBJECT_ID)
                .analyzable(true).metrics(metrics).analyzedAt(dayStart).build());
        cognitiveMetricRepository.save(CognitiveMetric.builder().turnId("unanalyzable").subjectId(SUBJECT_ID)
                .analyzable(false).analyzedAt(dayStart.plusSeconds(60)).build());
        cognitiveMetricRepository.save(CognitiveMetric.builder().turnId("other").subjectId(UUID.randomUUID())
                .analyzable(true).metrics(metrics).analyzedAt(dayStart.plusSeconds(60)).build());
        cognitiveMetricRepository.save(CognitiveMetric.builder().turnId("after").subjectId(SUBJECT_ID)
                .analyzable(true).metrics(metrics).analyzedAt(dayEnd).build());
        entityManager.flush();

        // when & then
        assertThat(cognitiveMetricRepository
                .findBySubjectIdAndAnalyzableTrueAndAnalyzedAtGreaterThanEqualAndAnalyzedAtLessThan(
                        SUBJECT_ID, dayStart, dayEnd))
                .extracting(CognitiveMetric::getTurnId).containsExactly("in");
        assertThat(cognitiveMetricRepository.findBySubjectIdAndAnalyzableTrueAndAnalyzedAtLessThan(SUBJECT_ID, dayEnd))
                .extracting(CognitiveMetric::getTurnId).containsExactlyInAnyOrder("before", "in");
    }

    @Test
    @DisplayName("같은 턴의 지표는 두 번 저장할 수 없다 (Kafka 중복 수신 대비)")
    void duplicateTurnId() {
        // given
        cognitiveMetricRepository.saveAndFlush(metric("turn-1", null));

        // when & then
        assertThatThrownBy(() -> cognitiveMetricRepository.saveAndFlush(metric("turn-1", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
