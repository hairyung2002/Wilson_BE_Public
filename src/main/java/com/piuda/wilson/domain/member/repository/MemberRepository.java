package com.piuda.wilson.domain.member.repository;

import com.piuda.wilson.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Member> findByIdAndDeletedFalse(Long id);
}
