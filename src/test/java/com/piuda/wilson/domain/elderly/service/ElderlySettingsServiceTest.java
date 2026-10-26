package com.piuda.wilson.domain.elderly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.piuda.wilson.domain.elderly.dto.ElderlySettingsRequest;
import com.piuda.wilson.domain.elderly.dto.ElderlySettingsResponse;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElderlySettingsServiceTest {

    @Mock
    private ElderlyAccessService elderlyAccessService;

    @InjectMocks
    private ElderlySettingsService elderlySettingsService;

    private final Elderly elderly = Elderly.builder().name("김철수").build();

    @Test
    @DisplayName("기본 설정은 선제 대화 켜짐, 방해 금지 22:00~07:00이다")
    void defaults() {
        // given
        given(elderlyAccessService.requireViewer(1L, 15L)).willReturn(new ElderlyAccess(elderly, AccessActor.MEMBER));

        // when & then
        assertThat(elderlySettingsService.get(1L, 15L))
                .isEqualTo(new ElderlySettingsResponse(true, LocalTime.of(22, 0), LocalTime.of(7, 0)));
    }

    @Test
    @DisplayName("주보호자·노인 본인은 보낸 항목만 바꾼다")
    void update() {
        // given
        given(elderlyAccessService.requireManager(1L, 15L)).willReturn(new ElderlyAccess(elderly, AccessActor.SELF));

        // when
        ElderlySettingsResponse response = elderlySettingsService.update(1L, 15L,
                new ElderlySettingsRequest(false, LocalTime.of(21, 0), null));

        // then
        assertThat(response).isEqualTo(new ElderlySettingsResponse(false, LocalTime.of(21, 0), LocalTime.of(7, 0)));
        assertThat(elderly.isProactiveEnabled()).isFalse();
    }

    @Test
    @DisplayName("방해 금지 시작과 끝이 같아지면 잘못된 요청이고 바꾸지 않는다")
    void update_sameStartAndEnd() {
        // given
        given(elderlyAccessService.requireManager(1L, 15L)).willReturn(new ElderlyAccess(elderly, AccessActor.OWNER));

        // when & then
        assertThatThrownBy(() -> elderlySettingsService.update(1L, 15L,
                new ElderlySettingsRequest(null, LocalTime.of(7, 0), null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThat(elderly.getQuietHoursStart()).isEqualTo(LocalTime.of(22, 0));
    }
}
