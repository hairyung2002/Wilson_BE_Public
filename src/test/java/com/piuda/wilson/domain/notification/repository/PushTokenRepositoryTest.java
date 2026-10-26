package com.piuda.wilson.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.notification.entity.PushPlatform;
import com.piuda.wilson.domain.notification.entity.PushToken;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class PushTokenRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Test
    @DisplayName("회원별·토큰별로 푸시 토큰을 찾고, 회원의 토큰을 지운다")
    void findAndDelete() {
        // given
        Member member = entityManager.persist(Member.builder().name("보호자").memberType(MemberType.CAREGIVER).build());
        pushTokenRepository.save(PushToken.builder().member(member).token("fcm-1").platform(PushPlatform.ANDROID).build());
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThat(pushTokenRepository.findByMemberId(member.getId()))
                .hasValueSatisfying(token -> assertThat(token.getToken()).isEqualTo("fcm-1"));
        assertThat(pushTokenRepository.findByToken("fcm-1")).isPresent();

        pushTokenRepository.deleteByMemberId(member.getId());
        entityManager.flush();
        assertThat(pushTokenRepository.findByMemberId(member.getId())).isEmpty();
    }
}
