package com.piuda.wilson.domain.caregiver.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class InviteCodeRepositoryTest {

    private static final Duration TTL = Duration.ofHours(24);

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private InviteCodeRepository inviteCodeRepository;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("새 코드를 저장하면 노인의 이전 코드는 무효가 된다 (노인당 유효 코드 1개)")
    void issue_replacesPreviousCode() {
        // given
        given(valueOperations.setIfAbsent("invite-code:K7Q2M9XD", "15", TTL)).willReturn(true);
        given(valueOperations.getAndSet("invite-code:elderly:15", "K7Q2M9XD")).willReturn("AAAA2222");

        // when
        boolean issued = inviteCodeRepository.issue("K7Q2M9XD", 15L, TTL);

        // then
        assertThat(issued).isTrue();
        verify(redisTemplate).expire("invite-code:elderly:15", TTL);
        verify(redisTemplate).delete("invite-code:AAAA2222");
    }

    @Test
    @DisplayName("이미 쓰이는 코드와 겹치면 저장하지 않는다")
    void issue_collision() {
        // given
        given(valueOperations.setIfAbsent("invite-code:K7Q2M9XD", "15", TTL)).willReturn(false);

        // when
        boolean issued = inviteCodeRepository.issue("K7Q2M9XD", 15L, TTL);

        // then
        assertThat(issued).isFalse();
        verify(valueOperations, never()).getAndSet(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("코드로 노인 식별자를 찾는다")
    void findElderlyId() {
        // given
        given(valueOperations.get("invite-code:K7Q2M9XD")).willReturn("15");
        given(valueOperations.get("invite-code:ZZZZ9999")).willReturn(null);

        // when & then
        assertThat(inviteCodeRepository.findElderlyId("K7Q2M9XD")).contains(15L);
        assertThat(inviteCodeRepository.findElderlyId("ZZZZ9999")).isEmpty();
    }

    @Test
    @DisplayName("사용한 코드를 지우고, 노인의 현재 코드가 이 코드면 그 연결도 지운다")
    void delete() {
        // given
        given(valueOperations.get("invite-code:elderly:15")).willReturn("K7Q2M9XD");

        // when
        inviteCodeRepository.delete("K7Q2M9XD", 15L);

        // then
        verify(redisTemplate).delete("invite-code:K7Q2M9XD");
        verify(redisTemplate).delete("invite-code:elderly:15");
    }
}
