package com.piuda.wilson.domain.elderly.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.entity.Gender;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ElderlyRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ElderlyRepository elderlyRepository;

    @Test
    @DisplayName("계정 없이 등록된 노인을 저장하고 조회한다")
    void saveWithoutMember() {
        // given
        Elderly elderly = Elderly.builder()
                .name("김철수").birthDate(LocalDate.of(1945, 3, 11)).gender(Gender.MALE).build();

        // when
        Elderly saved = elderlyRepository.saveAndFlush(elderly);
        entityManager.clear();

        // then
        Elderly found = elderlyRepository.findByIdAndDeletedFalse(saved.getId()).orElseThrow();
        assertThat(found.getMember()).isNull();
        assertThat(found.getName()).isEqualTo("김철수");
        assertThat(found.getGender()).isEqualTo(Gender.MALE);
    }

    @Test
    @DisplayName("노인마다 AI 연동용 가명 식별자가 서로 다르게 발급되어 저장된다")
    void subjectIdIssuedPerElderly() {
        // given
        Elderly first = entityManager.persist(Elderly.builder().name("김철수").build());
        Elderly second = entityManager.persist(Elderly.builder().name("이영희").build());
        entityManager.flush();
        entityManager.clear();

        // when
        Elderly foundFirst = elderlyRepository.findById(first.getId()).orElseThrow();
        Elderly foundSecond = elderlyRepository.findById(second.getId()).orElseThrow();

        // then
        assertThat(foundFirst.getSubjectId()).isNotNull().isEqualTo(first.getSubjectId());
        assertThat(foundSecond.getSubjectId()).isNotNull().isNotEqualTo(foundFirst.getSubjectId());
    }

    @Test
    @DisplayName("보호자 없이 남은 지 기준 시각보다 오래된 노인만 찾는다 (이미 삭제된 노인 제외)")
    void findOrphanedIdsBefore() {
        // given
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 20, 0, 0);
        Elderly expired = entityManager.persist(Elderly.builder().name("오래됨").build());
        expired.markOrphaned(cutoff.minusDays(1));
        Elderly recent = entityManager.persist(Elderly.builder().name("최근").build());
        recent.markOrphaned(cutoff.plusDays(1));
        Elderly deleted = entityManager.persist(Elderly.builder().name("삭제됨").build());
        deleted.markOrphaned(cutoff.minusDays(5));
        deleted.delete();
        entityManager.persist(Elderly.builder().name("보호자있음").build());
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThat(elderlyRepository.findOrphanedIdsBefore(cutoff)).containsExactly(expired.getId());
    }

    @Test
    @DisplayName("삭제되지 않은 노인의 식별자만 찾는다 (매일 판정 배치 대상)")
    void findActiveIds() {
        // given
        Elderly active = entityManager.persist(Elderly.builder().name("김철수").build());
        Elderly deleted = entityManager.persist(Elderly.builder().name("이영희").build());
        deleted.delete();
        entityManager.flush();

        // when & then
        assertThat(elderlyRepository.findActiveIds()).containsExactly(active.getId());
    }

    @Test
    @DisplayName("회원 식별자로 삭제되지 않은 노인을 조회한다")
    void findByMemberIdAndDeletedFalse() {
        // given
        Member member = entityManager.persist(Member.builder().name("김철수").memberType(MemberType.ELDERLY).build());
        Elderly active = entityManager.persist(Elderly.builder().member(member).name("김철수").build());
        Member deletedMember = entityManager.persist(
                Member.builder().name("이영희").memberType(MemberType.ELDERLY).build());
        entityManager.persist(Elderly.builder().member(deletedMember).name("이영희").build()).delete();
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThat(elderlyRepository.findByMemberIdAndDeletedFalse(member.getId()))
                .map(Elderly::getId).contains(active.getId());
        assertThat(elderlyRepository.findByMemberIdAndDeletedFalse(deletedMember.getId())).isEmpty();
    }

    @Test
    @DisplayName("삭제된 노인은 조회되지 않는다")
    void findByIdAndDeletedFalse_excludesDeleted() {
        // given
        Elderly elderly = entityManager.persist(Elderly.builder().name("김철수").build());
        elderly.delete();
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThat(elderlyRepository.findByIdAndDeletedFalse(elderly.getId())).isEmpty();
    }
}
