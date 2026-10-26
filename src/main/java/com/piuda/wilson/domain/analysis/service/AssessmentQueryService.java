package com.piuda.wilson.domain.analysis.service;

import com.piuda.wilson.domain.analysis.dto.AssessmentResponse;
import com.piuda.wilson.domain.analysis.repository.DailyAssessmentRepository;
import com.piuda.wilson.domain.elderly.service.ElderlyAccessService;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판정 이력 조회 (api-spec 12-5). 연결된 보호자만, 시작일·종료일 포함 최대 90일. */
@Service
@RequiredArgsConstructor
public class AssessmentQueryService {

    static final int MAX_RANGE_DAYS = 90;

    private final DailyAssessmentRepository dailyAssessmentRepository;
    private final ElderlyAccessService elderlyAccessService;

    @Transactional(readOnly = true)
    public List<AssessmentResponse> getAssessments(Long memberId, Long elderlyId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        elderlyAccessService.requireCaregiver(memberId, elderlyId);
        return dailyAssessmentRepository.findByElderlyIdAndAssessmentDateBetweenOrderByAssessmentDateAsc(
                        elderlyId, from, to).stream()
                .map(AssessmentResponse::from)
                .toList();
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
