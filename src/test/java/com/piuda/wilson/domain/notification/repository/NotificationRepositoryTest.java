package com.piuda.wilson.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.notification.entity.Notification;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.entity.PushStatus;
import com.piuda.wilson.global.config.JpaAuditingConfig;
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
class NotificationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private NotificationRepository notificationRepository;

    private Notification save(Member recipient, String title, PushStatus status) {
        return notificationRepository.save(Notification.builder().recipient(recipient)
                .type(NotificationType.DAILY_SUMMARY).title(title).body("b").pushStatus(status).build());
    }

    @Test
    @DisplayName("내 알림을 최신순으로 보고, 안 읽은 수를 세고, 모두 읽음 처리한다 (남의 알림은 그대로)")
    void listCountAndReadAll() {
        // given
        Member me = entityManager.persist(Member.builder().name("나").memberType(MemberType.CAREGIVER).build());
        Member other = entityManager.persist(Member.builder().name("남").memberType(MemberType.CAREGIVER).build());
        save(me, "첫째", PushStatus.SENT);
        save(me, "둘째", PushStatus.DEFERRED);
        Notification others = save(other, "남의 것", PushStatus.SENT);
        entityManager.flush();

        // when
        Page<Notification> page = notificationRepository.findByRecipientIdOrderByIdDesc(me.getId(), PageRequest.of(0, 10));
        long unread = notificationRepository.countByRecipientIdAndIsReadFalse(me.getId());
        int updated = notificationRepository.markAllRead(me.getId());
        entityManager.clear();

        // then
        assertThat(page.getContent()).extracting(Notification::getTitle).containsExactly("둘째", "첫째");
        assertThat(unread).isEqualTo(2);
        assertThat(updated).isEqualTo(2);
        assertThat(notificationRepository.countByRecipientIdAndIsReadFalse(me.getId())).isZero();
        assertThat(notificationRepository.findById(others.getId()).orElseThrow().isRead()).isFalse();
        assertThat(notificationRepository.findByPushStatusOrderByIdAsc(PushStatus.DEFERRED))
                .extracting(Notification::getTitle).containsExactly("둘째");
        assertThat(notificationRepository.findByIdAndRecipientId(others.getId(), me.getId())).isEmpty();
    }
}
