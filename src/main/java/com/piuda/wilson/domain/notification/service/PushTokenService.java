package com.piuda.wilson.domain.notification.service;

import com.piuda.wilson.domain.member.repository.MemberRepository;
import com.piuda.wilson.domain.notification.dto.RegisterPushTokenRequest;
import com.piuda.wilson.domain.notification.entity.PushToken;
import com.piuda.wilson.domain.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FCM 푸시 토큰 등록·삭제 (api-spec 4-5). 노인·보호자 공통이며 회원당 마지막 기기 1개만 보관한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;
    private final MemberRepository memberRepository;

    /**
     * 같은 기기 토큰이 다른 회원에게 남아 있으면 지운다. 한 기기에서 계정을 바꾸면
     * 이전 계정의 알림(예: 다른 노인의 응급 알림)이 이 기기로 오면 안 되기 때문이다.
     */
    @Transactional
    public void register(Long memberId, RegisterPushTokenRequest request) {
        pushTokenRepository.findByToken(request.token())
                .filter(pushToken -> !pushToken.getMember().getId().equals(memberId))
                .ifPresent(pushToken -> {
                    pushTokenRepository.delete(pushToken);
                    pushTokenRepository.flush();
                    log.info("Push token moved to another member: fromMemberId={}, toMemberId={}",
                            pushToken.getMember().getId(), memberId);
                });

        pushTokenRepository.findByMemberId(memberId).ifPresentOrElse(
                pushToken -> pushToken.change(request.token(), request.platform()),
                () -> pushTokenRepository.save(PushToken.builder()
                        .member(memberRepository.getReferenceById(memberId))
                        .token(request.token())
                        .platform(request.platform())
                        .build()));
    }

    @Transactional
    public void unregister(Long memberId) {
        pushTokenRepository.deleteByMemberId(memberId);
    }
}
