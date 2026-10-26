package com.piuda.wilson.global.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuietHoursTest {

    @Test
    @DisplayName("자정을 넘기는 구간(22:00~07:00)은 시작 이상, 끝 미만을 조용한 시간으로 본다")
    void overMidnight() {
        // given
        QuietHours quietHours = new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0));

        // when & then
        assertThat(quietHours.contains(LocalTime.of(22, 0))).isTrue();
        assertThat(quietHours.contains(LocalTime.of(23, 59))).isTrue();
        assertThat(quietHours.contains(LocalTime.of(0, 0))).isTrue();
        assertThat(quietHours.contains(LocalTime.of(6, 59))).isTrue();
        assertThat(quietHours.contains(LocalTime.of(7, 0))).isFalse();
        assertThat(quietHours.contains(LocalTime.of(21, 59))).isFalse();
    }

    @Test
    @DisplayName("같은 날 안의 구간(13:00~14:00)도 처리한다")
    void sameDay() {
        // given
        QuietHours quietHours = new QuietHours(LocalTime.of(13, 0), LocalTime.of(14, 0));

        // when & then
        assertThat(quietHours.contains(LocalTime.of(13, 30))).isTrue();
        assertThat(quietHours.contains(LocalTime.of(14, 0))).isFalse();
        assertThat(quietHours.contains(LocalTime.of(12, 59))).isFalse();
    }

    @Test
    @DisplayName("시작과 끝이 같으면 잘못된 구간이다")
    void sameStartAndEnd() {
        // when & then
        assertThatThrownBy(() -> new QuietHours(LocalTime.of(7, 0), LocalTime.of(7, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
