package com.piuda.wilson.domain.elderly.service;

import com.piuda.wilson.domain.elderly.dto.ElderlySettingsRequest;
import com.piuda.wilson.domain.elderly.dto.ElderlySettingsResponse;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.global.common.time.QuietHours;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 노인 설정 (api-spec 16-2): 선제 대화 켜기·끄기, 방해 금지 시간. 조회는 연결된 보호자 전체, 변경은 OWNER·본인. */
@Service
@RequiredArgsConstructor
public class ElderlySettingsService {

    private final ElderlyAccessService elderlyAccessService;

    @Transactional(readOnly = true)
    public ElderlySettingsResponse get(Long memberId, Long elderlyId) {
        return ElderlySettingsResponse.from(elderlyAccessService.requireViewer(memberId, elderlyId).elderly());
    }

    @Transactional
    public ElderlySettingsResponse update(Long memberId, Long elderlyId, ElderlySettingsRequest request) {
        Elderly elderly = elderlyAccessService.requireManager(memberId, elderlyId).elderly();
        elderly.updateSettings(request.proactiveEnabled(), quietHours(elderly, request));
        return ElderlySettingsResponse.from(elderly);
    }

    private static QuietHours quietHours(Elderly elderly, ElderlySettingsRequest request) {
        LocalTime start = request.quietHoursStart() != null ? request.quietHoursStart() : elderly.getQuietHoursStart();
        LocalTime end = request.quietHoursEnd() != null ? request.quietHoursEnd() : elderly.getQuietHoursEnd();
        try {
            return new QuietHours(start.withSecond(0).withNano(0), end.withSecond(0).withNano(0));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
