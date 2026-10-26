package com.piuda.wilson.domain.notification.repository;

import com.piuda.wilson.domain.notification.entity.PushToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    Optional<PushToken> findByMemberId(Long memberId);

    Optional<PushToken> findByToken(String token);

    void deleteByMemberId(Long memberId);
}
