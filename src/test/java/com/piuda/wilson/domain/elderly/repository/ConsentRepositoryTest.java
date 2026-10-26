package com.piuda.wilson.domain.elderly.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.elderly.entity.Consent;
import com.piuda.wilson.domain.elderly.entity.ConsentGrantor;
import com.piuda.wilson.domain.elderly.entity.ConsentType;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.entity.LegalBasis;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ConsentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConsentRepository consentRepository;

    private Consent consent(Elderly elderly, Member grantor, ConsentType type, boolean agreed) {
        return Consent.builder().elderly(elderly).type(type).agreed(agreed).version(1)
                .grantor(ConsentGrantor.OWNER).grantedByMemberId(grantor.getId())
                .legalBasis(LegalBasis.FAMILY_DELEGATION).build();
    }

    @Test
    @DisplayName("동의 기록은 고치지 않고 쌓이며, 종류별로 가장 최근 기록을 찾는다")
    void findLatest() {
        // given
        Member grantor = entityManager.persist(Member.builder().name("보호자").memberType(MemberType.CAREGIVER).build());
        Elderly elderly = entityManager.persist(Elderly.builder().name("김철수").build());
        Elderly other = entityManager.persist(Elderly.builder().name("이영희").build());
        consentRepository.save(consent(elderly, grantor, ConsentType.TRANSCRIPT_SHARING, true));
        consentRepository.save(consent(elderly, grantor, ConsentType.TRANSCRIPT_SHARING, false));
        consentRepository.save(consent(elderly, grantor, ConsentType.SENSITIVE_INFO, true));
        consentRepository.save(consent(other, grantor, ConsentType.TRANSCRIPT_SHARING, true));
        entityManager.flush();
        entityManager.clear();

        // when
        var latestSharing = consentRepository
                .findFirstByElderlyIdAndTypeOrderByIdDesc(elderly.getId(), ConsentType.TRANSCRIPT_SHARING);
        List<Consent> history = consentRepository.findByElderlyIdOrderByIdDesc(elderly.getId());

        // then
        assertThat(latestSharing).hasValueSatisfying(consent -> assertThat(consent.isAgreed()).isFalse());
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getType()).isEqualTo(ConsentType.SENSITIVE_INFO);
        assertThat(history.get(0).getCreatedAt()).isNotNull();
    }
}
