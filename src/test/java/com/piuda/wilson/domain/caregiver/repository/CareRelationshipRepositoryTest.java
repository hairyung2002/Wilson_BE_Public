package com.piuda.wilson.domain.caregiver.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class CareRelationshipRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CareRelationshipRepository careRelationshipRepository;

    @Test
    @DisplayName("보호자-노인 연결을 저장하고 존재 여부와 목록을 조회한다")
    void saveAndExists() {
        // given
        Elderly elderly = persistElderly("elder@wilson.com");
        Caregiver caregiver = persistCaregiver("care@wilson.com");
        CareRelationship relationship = CareRelationship.builder()
                .caregiver(caregiver).elderly(elderly).relation("자녀").role(CareRole.OWNER).build();

        // when
        careRelationshipRepository.save(relationship);

        // then
        assertThat(careRelationshipRepository
                .existsByCaregiverIdAndElderlyId(caregiver.getId(), elderly.getId())).isTrue();
        assertThat(careRelationshipRepository.findByCaregiverId(caregiver.getId())).hasSize(1);
    }

    @Test
    @DisplayName("동일 보호자-노인 연결 중복은 제약 위반으로 실패한다")
    void duplicateLink_throws() {
        // given
        Elderly elderly = persistElderly("elder2@wilson.com");
        Caregiver caregiver = persistCaregiver("care2@wilson.com");
        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(caregiver).elderly(elderly).relation("자녀").role(CareRole.OWNER).build());
        CareRelationship duplicate = CareRelationship.builder()
                .caregiver(caregiver).elderly(elderly).role(CareRole.MEMBER).build();

        // when & then
        assertThatThrownBy(() -> careRelationshipRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("보호자의 연결 목록을 노인과 함께 조회하고 삭제된 노인은 제외한다")
    void findAllWithActiveElderlyByCaregiverId() {
        // given
        Caregiver caregiver = persistCaregiver("care3@wilson.com");
        Elderly active = entityManager.persist(Elderly.builder().name("김철수").build());
        Elderly deleted = entityManager.persist(Elderly.builder().name("이영희").build());
        deleted.delete();
        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(caregiver).elderly(active).relation("FATHER").role(CareRole.OWNER).build());
        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(caregiver).elderly(deleted).relation("MOTHER").role(CareRole.OWNER).build());
        entityManager.flush();
        entityManager.clear();

        // when
        List<CareRelationship> relationships =
                careRelationshipRepository.findAllWithActiveElderlyByCaregiverId(caregiver.getId());

        // then
        assertThat(relationships).hasSize(1);
        assertThat(Hibernate.isInitialized(relationships.get(0).getElderly())).isTrue();
        assertThat(relationships.get(0).getElderly().getName()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("보호자와 노인으로 연결을 조회한다")
    void findByCaregiverIdAndElderlyId() {
        // given
        Caregiver caregiver = persistCaregiver("care4@wilson.com");
        Caregiver other = persistCaregiver("care5@wilson.com");
        Elderly elderly = entityManager.persist(Elderly.builder().name("김철수").build());
        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(caregiver).elderly(elderly).relation("FATHER").role(CareRole.OWNER).build());

        // when & then
        assertThat(careRelationshipRepository
                .findByCaregiverIdAndElderlyId(caregiver.getId(), elderly.getId())).isPresent();
        assertThat(careRelationshipRepository
                .findByCaregiverIdAndElderlyId(other.getId(), elderly.getId())).isEmpty();
    }

    @Test
    @DisplayName("보호자의 연결만 모두 끊고 노인과 다른 보호자의 연결은 남긴다")
    void deleteByCaregiverId() {
        // given
        Caregiver leaving = persistCaregiver("care6@wilson.com");
        Caregiver staying = persistCaregiver("care7@wilson.com");
        Elderly elderly = entityManager.persist(Elderly.builder().name("김철수").build());
        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(leaving).elderly(elderly).relation("FATHER").role(CareRole.OWNER).build());
        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(staying).elderly(elderly).relation("FATHER").role(CareRole.MEMBER).build());

        // when
        careRelationshipRepository.deleteByCaregiverId(leaving.getId());
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(careRelationshipRepository.findByCaregiverId(leaving.getId())).isEmpty();
        assertThat(careRelationshipRepository.findByCaregiverId(staying.getId())).hasSize(1);
        assertThat(entityManager.find(Elderly.class, elderly.getId())).isNotNull();
    }

    @Test
    @DisplayName("노인에게 주보호자가 있는지 확인한다")
    void existsByElderlyIdAndRole() {
        // given
        Elderly withOwner = entityManager.persist(Elderly.builder().name("김철수").build());
        Elderly withoutOwner = entityManager.persist(Elderly.builder().name("이영희").build());
        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(persistCaregiver("care8@wilson.com")).elderly(withOwner).role(CareRole.OWNER).build());
        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(persistCaregiver("care9@wilson.com")).elderly(withoutOwner).role(CareRole.MEMBER).build());

        // when & then
        assertThat(careRelationshipRepository.existsByElderlyIdAndRole(withOwner.getId(), CareRole.OWNER)).isTrue();
        assertThat(careRelationshipRepository.existsByElderlyIdAndRole(withoutOwner.getId(), CareRole.OWNER))
                .isFalse();
    }

    @Test
    @DisplayName("노인의 보호자 목록을 보호자 회원 정보와 함께 연결 순서대로 조회한다")
    void findAllWithCaregiverByElderlyId() {
        // given
        Elderly elderly = entityManager.persist(Elderly.builder().name("김철수").build());
        Elderly other = entityManager.persist(Elderly.builder().name("이영희").build());
        Caregiver owner = persistCaregiver("care10@wilson.com");
        Caregiver member = persistCaregiver("care11@wilson.com");
        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(owner).elderly(elderly).relation("아들").role(CareRole.OWNER).build());
        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(member).elderly(elderly).relation("딸").role(CareRole.MEMBER).build());
        careRelationshipRepository.save(CareRelationship.builder()
                .caregiver(member).elderly(other).relation("딸").role(CareRole.OWNER).build());
        entityManager.flush();
        entityManager.clear();

        // when
        List<CareRelationship> relationships = careRelationshipRepository.findAllWithCaregiverByElderlyId(elderly.getId());

        // then
        assertThat(relationships).extracting(CareRelationship::getRelation).containsExactly("아들", "딸");
        assertThat(Hibernate.isInitialized(relationships.get(0).getCaregiver().getMember())).isTrue();
    }

    private Elderly persistElderly(String email) {
        Member member = entityManager.persist(Member.builder()
                .email(email).password("p").name("노인").memberType(MemberType.ELDERLY).build());
        return entityManager.persist(Elderly.builder()
                .member(member).name("노인").birthDate(LocalDate.of(1940, 1, 1)).build());
    }

    private Caregiver persistCaregiver(String email) {
        Member member = entityManager.persist(Member.builder()
                .email(email).password("p").name("보호자").memberType(MemberType.CAREGIVER).build());
        return entityManager.persist(Caregiver.builder().member(member).build());
    }
}
