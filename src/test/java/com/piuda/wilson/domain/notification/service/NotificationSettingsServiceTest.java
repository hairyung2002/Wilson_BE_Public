package com.piuda.wilson.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.notification.dto.NotificationSettingsRequest;
import com.piuda.wilson.domain.notification.dto.NotificationSettingsResponse;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {

    @Mock
    private CaregiverRepository caregiverRepository;

    @Mock
    private CareRelationshipRepository careRelationshipRepository;

    @InjectMocks
    private NotificationSettingsService notificationSettingsService;

    private CareRelationship givenRelationship(CareRole role) {
        Caregiver caregiver = Caregiver.builder().build();
        ReflectionTestUtils.setField(caregiver, "id", 3L);
        CareRelationship relationship = CareRelationship.builder().caregiver(caregiver).role(role).build();
        given(caregiverRepository.findByMemberId(1L)).willReturn(Optional.of(caregiver));
        given(careRelationshipRepository.findByCaregiverIdAndElderlyId(3L, 15L)).willReturn(Optional.of(relationship));
        return relationship;
    }

    @Test
    @DisplayName("설정하지 않았으면 OWNER는 모두 켜짐, MEMBER는 모두 꺼짐으로 보인다")
    void defaults() {
        // given
        givenRelationship(CareRole.MEMBER);

        // when & then
        assertThat(notificationSettingsService.get(1L, 15L))
                .isEqualTo(new NotificationSettingsResponse(false, false, false));
    }

    @Test
    @DisplayName("보낸 항목만 바꾸고, 바꾼 값은 역할 기본값보다 우선한다")
    void update() {
        // given
        givenRelationship(CareRole.OWNER);

        // when
        NotificationSettingsResponse response = notificationSettingsService.update(1L, 15L,
                new NotificationSettingsRequest(null, false, null));

        // then
        assertThat(response).isEqualTo(new NotificationSettingsResponse(true, false, true));
    }

    @Test
    @DisplayName("연결되지 않은 노인의 알림 설정은 볼 수 없다")
    void notLinked() {
        // given
        Caregiver caregiver = Caregiver.builder().build();
        ReflectionTestUtils.setField(caregiver, "id", 3L);
        given(caregiverRepository.findByMemberId(1L)).willReturn(Optional.of(caregiver));
        given(careRelationshipRepository.findByCaregiverIdAndElderlyId(3L, 15L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationSettingsService.get(1L, 15L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.NO_PERMISSION));
    }
}
