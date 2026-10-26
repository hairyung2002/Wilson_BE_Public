package com.piuda.wilson.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.global.config.JpaAuditingConfig;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("이메일로 회원을 조회하고 생성 시각이 기록된다")
    void saveAndFindByEmail() {
        // given
        Member member = Member.builder()
                .email("elderly@wilson.com")
                .password("encoded-password")
                .name("김윌슨")
                .memberType(MemberType.ELDERLY)
                .build();

        // when
        memberRepository.save(member);
        Optional<Member> found = memberRepository.findByEmail("elderly@wilson.com");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("김윌슨");
        assertThat(found.get().getMemberType()).isEqualTo(MemberType.ELDERLY);
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(memberRepository.existsByEmail("elderly@wilson.com")).isTrue();
    }

    @Test
    @DisplayName("탈퇴한 회원은 식별자로 조회되지 않고, 같은 이메일로 다시 가입할 수 있다")
    void withdrawnMember() {
        // given
        Member member = memberRepository.save(Member.builder()
                .email("leave@wilson.com").password("p").name("보호자").phone("09012345678")
                .memberType(MemberType.CAREGIVER).build());
        member.withdraw();
        memberRepository.flush();

        // when
        Member rejoined = memberRepository.saveAndFlush(Member.builder()
                .email("leave@wilson.com").password("p").name("보호자").memberType(MemberType.CAREGIVER).build());

        // then
        assertThat(memberRepository.findByIdAndDeletedFalse(member.getId())).isEmpty();
        assertThat(memberRepository.findByIdAndDeletedFalse(rejoined.getId())).isPresent();
        assertThat(memberRepository.findByEmail("leave@wilson.com")).map(Member::getId).contains(rejoined.getId());
    }

    @Test
    @DisplayName("이메일 중복 저장은 제약 위반으로 실패한다")
    void duplicateEmail_throws() {
        // given
        memberRepository.save(Member.builder()
                .email("dup@wilson.com").password("p").name("먼저").memberType(MemberType.CAREGIVER).build());
        Member duplicate = Member.builder()
                .email("dup@wilson.com").password("p").name("나중").memberType(MemberType.CAREGIVER).build();

        // when & then
        assertThatThrownBy(() -> memberRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
