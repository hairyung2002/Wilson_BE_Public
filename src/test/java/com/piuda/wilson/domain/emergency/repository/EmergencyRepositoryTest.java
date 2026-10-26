package com.piuda.wilson.domain.emergency.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.emergency.entity.Emergency;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import com.piuda.wilson.infrastructure.ai.EmergencyCategory;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class EmergencyRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 0);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EmergencyRepository emergencyRepository;

    private Elderly elderly;
    private Elderly other;

    @BeforeEach
    void setUp() {
        elderly = entityManager.persist(Elderly.builder().name("김철수").build());
        other = entityManager.persist(Elderly.builder().name("이영희").build());
    }

    private Emergency emergency(Elderly target, EmergencyCategory category, LocalDateTime detectedAt, boolean notified) {
        return emergencyRepository.save(Emergency.builder().elderly(target).conversationId(100L).turnId("turn-1")
                .category(category).detectedAt(detectedAt).notified(notified).build());
    }

    @Test
    @DisplayName("같은 노인·같은 종류로 기준 시각 이후 알린 응급이 있는지 확인한다 (10분 중복 알림 방지)")
    void existsNotifiedSince() {
        // given
        emergency(elderly, EmergencyCategory.FALL, NOW.minusMinutes(5), true);
        emergency(elderly, EmergencyCategory.PAIN, NOW.minusMinutes(5), false);
        emergency(elderly, EmergencyCategory.BREATHING, NOW.minusMinutes(15), true);
        emergency(other, EmergencyCategory.HELP_REQUEST, NOW.minusMinutes(1), true);
        entityManager.flush();
        LocalDateTime since = NOW.minusMinutes(10);
        Long id = elderly.getId();

        // when & then
        assertThat(emergencyRepository.existsByElderlyIdAndCategoryAndNotifiedTrueAndDetectedAtGreaterThanEqual(
                id, EmergencyCategory.FALL, since)).isTrue();
        assertThat(emergencyRepository.existsByElderlyIdAndCategoryAndNotifiedTrueAndDetectedAtGreaterThanEqual(
                id, EmergencyCategory.PAIN, since)).isFalse();
        assertThat(emergencyRepository.existsByElderlyIdAndCategoryAndNotifiedTrueAndDetectedAtGreaterThanEqual(
                id, EmergencyCategory.BREATHING, since)).isFalse();
        assertThat(emergencyRepository.existsByElderlyIdAndCategoryAndNotifiedTrueAndDetectedAtGreaterThanEqual(
                id, EmergencyCategory.HELP_REQUEST, since)).isFalse();
    }

    @Test
    @DisplayName("노인의 응급 기록을 최신순 페이지로 조회한다")
    void findPage() {
        // given
        Emergency older = emergency(elderly, EmergencyCategory.FALL, NOW.minusDays(1), true);
        Emergency newer = emergency(elderly, EmergencyCategory.PAIN, NOW, true);
        emergency(other, EmergencyCategory.FALL, NOW, true);
        entityManager.flush();
        entityManager.clear();

        // when
        Page<Emergency> page = emergencyRepository.findByElderlyIdOrderByDetectedAtDesc(elderly.getId(),
                PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).extracting(Emergency::getId).containsExactly(newer.getId(), older.getId());
    }

    @Test
    @DisplayName("노인을 삭제하면 응급 기록도 지운다")
    void deleteByElderlyId() {
        // given
        emergency(elderly, EmergencyCategory.FALL, NOW, true);
        Emergency kept = emergency(other, EmergencyCategory.FALL, NOW, true);
        entityManager.flush();

        // when
        emergencyRepository.deleteByElderlyId(elderly.getId());
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(emergencyRepository.findAll()).extracting(Emergency::getId).containsExactly(kept.getId());
    }
}
