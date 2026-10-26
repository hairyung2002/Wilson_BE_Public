package com.piuda.wilson.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.analysis.dto.AssessmentResponse;
import com.piuda.wilson.domain.analysis.dto.AssessmentResponse.FlaggedMetricResponse;
import com.piuda.wilson.domain.analysis.entity.AssessmentLevel;
import com.piuda.wilson.domain.analysis.entity.DailyAssessment;
import com.piuda.wilson.domain.analysis.entity.JudgedMetric;
import com.piuda.wilson.domain.analysis.entity.MetricJudgement;
import com.piuda.wilson.domain.analysis.repository.DailyAssessmentRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.service.AccessActor;
import com.piuda.wilson.domain.elderly.service.ElderlyAccess;
import com.piuda.wilson.domain.elderly.service.ElderlyAccessService;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssessmentQueryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ELDERLY_ID = 15L;
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 20);

    @Mock
    private DailyAssessmentRepository dailyAssessmentRepository;

    @Mock
    private ElderlyAccessService elderlyAccessService;

    @InjectMocks
    private AssessmentQueryService assessmentQueryService;

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    @Test
    @DisplayName("기간 안의 판정을 날짜순으로, 보호자 표시 문구와 판정된 지표 이름을 붙여 돌려준다")
    void getAssessments() {
        // given
        Elderly elderly = Elderly.builder().name("김철수").build();
        given(elderlyAccessService.requireCaregiver(MEMBER_ID, ELDERLY_ID))
                .willReturn(new ElderlyAccess(elderly, AccessActor.OWNER));
        DailyAssessment assessment = DailyAssessment.builder().elderly(elderly).assessmentDate(TO)
                .metrics(List.of(new MetricJudgement(JudgedMetric.SUBSEQUENT_ONSET_LATENCY, 2.4, 5),
                        new MetricJudgement(JudgedMetric.FILLER_RATIO, 2.4, 4)))
                .build();
        given(dailyAssessmentRepository.findByElderlyIdAndAssessmentDateBetweenOrderByAssessmentDateAsc(
                ELDERLY_ID, FROM, TO)).willReturn(List.of(assessment));

        // when
        List<AssessmentResponse> responses = assessmentQueryService.getAssessments(MEMBER_ID, ELDERLY_ID, FROM, TO);

        // then
        assertThat(responses).containsExactly(new AssessmentResponse(TO, AssessmentLevel.MEDIUM,
                "최근 며칠 평소와 조금 다른 대화 모습이 보여요",
                List.of(new FlaggedMetricResponse("subsequent_onset_latency_ms", "대답까지 걸리는 시간"))));
    }

    @Test
    @DisplayName("기간이 90일을 넘거나 시작일이 종료일보다 늦으면 400")
    void invalidRange() {
        // when & then
        assertThatThrownBy(() -> assessmentQueryService.getAssessments(MEMBER_ID, ELDERLY_ID, TO.minusDays(90), TO))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> assessmentQueryService.getAssessments(MEMBER_ID, ELDERLY_ID, TO, FROM))
                .satisfies(e -> assertErrorCode(e, ErrorCode.INVALID_INPUT_VALUE));
        verify(dailyAssessmentRepository, never())
                .findByElderlyIdAndAssessmentDateBetweenOrderByAssessmentDateAsc(any(), any(), any());
    }

    @Test
    @DisplayName("90일(시작일·종료일 포함)까지는 조회할 수 있다")
    void ninetyDays() {
        // given
        given(elderlyAccessService.requireCaregiver(MEMBER_ID, ELDERLY_ID))
                .willReturn(new ElderlyAccess(Elderly.builder().build(), AccessActor.MEMBER));

        // when & then
        assertThat(assessmentQueryService.getAssessments(MEMBER_ID, ELDERLY_ID, TO.minusDays(89), TO)).isEmpty();
    }
}
