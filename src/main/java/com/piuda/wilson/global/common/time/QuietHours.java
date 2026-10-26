package com.piuda.wilson.global.common.time;

import java.time.LocalTime;

/**
 * 방해 금지 시간 구간 (KST). 시작 이상 끝 미만이며, 자정을 넘기는 구간(22:00~07:00)도 표현한다.
 * 보호자 알림 보류(api-spec 14-2)와 노인 선제 대화(6-2·16-2)에 쓴다.
 */
public record QuietHours(LocalTime start, LocalTime end) {

    /** 보호자 알림 기본값 (결정 E4·C6). */
    public static final QuietHours DEFAULT = new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0));

    public QuietHours {
        if (start == null || end == null || start.equals(end)) {
            throw new IllegalArgumentException("방해 금지 시간의 시작과 끝은 서로 달라야 합니다.");
        }
    }

    public boolean contains(LocalTime time) {
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }
}
