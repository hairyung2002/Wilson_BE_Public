package com.piuda.wilson.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.piuda.wilson.domain.analysis.entity.AssessmentLevel;
import com.piuda.wilson.domain.analysis.entity.CognitiveMetric;
import com.piuda.wilson.domain.analysis.entity.DailyAssessment;
import com.piuda.wilson.domain.analysis.entity.ElderBaseline;
import com.piuda.wilson.domain.analysis.entity.JudgedMetric;
import com.piuda.wilson.domain.analysis.entity.MetricJudgement;
import com.piuda.wilson.domain.analysis.entity.SpeechMetrics;
import com.piuda.wilson.domain.analysis.repository.CognitiveMetricRepository;
import com.piuda.wilson.domain.analysis.repository.DailyAssessmentRepository;
import com.piuda.wilson.domain.analysis.repository.ElderBaselineRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.service.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T18:00:00Z"), KST);
    private static final Long ELDERLY_ID = 15L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 20);
    private static final Instant DAY_START = Instant.parse("2026-10-19T15:00:00Z");
    private static final Instant DAY_END = Instant.parse("2026-10-20T15:00:00Z");

    @Mock
    private ElderlyRepository elderlyRepository;

    @Mock
    private ElderBaselineRepository elderBaselineRepository;

    @Mock
    private DailyAssessmentRepository dailyAssessmentRepository;

    @Mock
    private CognitiveMetricRepository cognitiveMetricRepository;

    @Mock
    private NotificationService notificationService;

    private AssessmentService assessmentService;
    private Elderly elderly;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        assessmentService = new AssessmentService(elderlyRepository, elderBaselineRepository,
                dailyAssessmentRepository, cognitiveMetricRepository, notificationService, CLOCK);
        elderly = Elderly.builder().name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", ELDERLY_ID);
        subjectId = elderly.getSubjectId();
    }

    private void givenElderlyNotAssessed() {
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly));
        given(dailyAssessmentRepository.existsByElderlyIdAndAssessmentDate(ELDERLY_ID, DATE)).willReturn(false);
    }

    private CognitiveMetric metric(Instant at, Double latency, Double filler, Double contentUnits, Double words) {
        return CognitiveMetric.builder().turnId(UUID.randomUUID().toString()).subjectId(subjectId).analyzable(true)
                .metrics(new SpeechMetrics(null, null, filler, null, contentUnits, words, latency, null, null, null,
                        null))
                .analyzedAt(at).build();
    }

    private List<ElderBaseline> givenBaselines() {
        BaselineValue value = new BaselineValue(100.0, 10.0, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14));
        List<ElderBaseline> baselines = Arrays.stream(JudgedMetric.values())
                .map(metric -> ElderBaseline.of(elderly, metric, value)).toList();
        given(elderBaselineRepository.findByElderlyId(ELDERLY_ID)).willReturn(baselines);
        return baselines;
    }

    private void givenPrevious(int latencyStreak, int fillerStreak, int contentStreak) {
        DailyAssessment previous = DailyAssessment.builder().elderly(elderly).assessmentDate(DATE.minusDays(1))
                .metrics(List.of(
                        new MetricJudgement(JudgedMetric.SUBSEQUENT_ONSET_LATENCY, 2.1, latencyStreak),
                        new MetricJudgement(JudgedMetric.FILLER_RATIO, 2.3, fillerStreak),
                        new MetricJudgement(JudgedMetric.CONTENT_INFO_UNIT_RATIO, 2.2, contentStreak),
                        new MetricJudgement(JudgedMetric.WORDS_PER_UTTERANCE, 0.1, 0)))
                .build();
        given(dailyAssessmentRepository.findFirstByElderlyIdAndAssessmentDateLessThanOrderByAssessmentDateDesc(
                ELDERLY_ID, DATE)).willReturn(Optional.of(previous));
    }

    private void givenDay(CognitiveMetric... metrics) {
        given(cognitiveMetricRepository.findBySubjectIdAndAnalyzableTrueAndAnalyzedAtGreaterThanEqualAndAnalyzedAtLessThan(
                subjectId, DAY_START, DAY_END)).willReturn(List.of(metrics));
    }

    private DailyAssessment savedAssessment() {
        ArgumentCaptor<DailyAssessment> captor = ArgumentCaptor.forClass(DailyAssessment.class);
        verify(dailyAssessmentRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("이미 판정한 날이면 다시 판정하지 않는다 (배치 재실행)")
    void alreadyAssessed() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.of(elderly));
        given(dailyAssessmentRepository.existsByElderlyIdAndAssessmentDate(ELDERLY_ID, DATE)).willReturn(true);

        // when
        assessmentService.assess(ELDERLY_ID, DATE);

        // then
        verify(elderBaselineRepository, never()).findByElderlyId(any());
        verify(dailyAssessmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("지표가 쌓인 지 14일이 안 됐으면 기준값도 판정도 만들지 않는다 (COLLECTING)")
    void collecting() {
        // given
        givenElderlyNotAssessed();
        given(elderBaselineRepository.findByElderlyId(ELDERLY_ID)).willReturn(List.of());
        List<CognitiveMetric> history = new ArrayList<>();
        for (int day = 0; day < 5; day++) {
            history.add(metric(DAY_START.minusSeconds(86_400L * day).plusSeconds(3600), 800.0, 0.1, 0.5, 6.0));
        }
        given(cognitiveMetricRepository.findBySubjectIdAndAnalyzableTrueAndAnalyzedAtLessThan(subjectId, DAY_END))
                .willReturn(history);

        // when
        assessmentService.assess(ELDERLY_ID, DATE);

        // then
        verify(elderBaselineRepository, never()).saveAll(any());
        verify(dailyAssessmentRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("처음 14일이 차면 지표별 기준값을 만들고, 기준값을 만든 날은 판정하지 않는다")
    void createBaselines() {
        // given
        givenElderlyNotAssessed();
        given(elderBaselineRepository.findByElderlyId(ELDERLY_ID)).willReturn(List.of());
        List<CognitiveMetric> history = new ArrayList<>();
        for (int day = 0; day < 14; day++) {
            history.add(metric(DAY_START.minusSeconds(86_400L * day).plusSeconds(3600), 800.0 + day % 2 * 100,
                    0.1, 0.5, null));
        }
        given(cognitiveMetricRepository.findBySubjectIdAndAnalyzableTrueAndAnalyzedAtLessThan(subjectId, DAY_END))
                .willReturn(history);

        // when
        assessmentService.assess(ELDERLY_ID, DATE);

        // then
        ArgumentCaptor<List<ElderBaseline>> captor = ArgumentCaptor.forClass(List.class);
        verify(elderBaselineRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(ElderBaseline::getMetric, ElderBaseline::getMean, ElderBaseline::getPeriodStart,
                        ElderBaseline::getPeriodEnd)
                .containsExactlyInAnyOrder(
                        tuple(JudgedMetric.SUBSEQUENT_ONSET_LATENCY, 850.0, LocalDate.of(2026, 10, 7), DATE),
                        tuple(JudgedMetric.FILLER_RATIO, 0.1, LocalDate.of(2026, 10, 7), DATE),
                        tuple(JudgedMetric.CONTENT_INFO_UNIT_RATIO, 0.5, LocalDate.of(2026, 10, 7), DATE));
        verify(dailyAssessmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("5일 연속 벗어난 지표가 하나 생기면 MEDIUM으로 판정하고, 위험도가 올랐으니 보호자에게 알린다")
    void flagged_notifies() {
        // given
        givenElderlyNotAssessed();
        givenBaselines();
        givenPrevious(4, 2, 3);
        givenDay(metric(DAY_START.plusSeconds(3600), 120.0, 105.0, null, null),
                metric(DAY_START.plusSeconds(7200), 140.0, null, null, null));

        // when
        assessmentService.assess(ELDERLY_ID, DATE);

        // then
        DailyAssessment saved = savedAssessment();
        assertThat(saved.getAssessmentDate()).isEqualTo(DATE);
        assertThat(saved.getLevel()).isEqualTo(AssessmentLevel.MEDIUM);
        assertThat(saved.getMetrics())
                .extracting(MetricJudgement::metric, MetricJudgement::zScore, MetricJudgement::streak)
                .containsExactlyInAnyOrder(
                        tuple(JudgedMetric.SUBSEQUENT_ONSET_LATENCY, 3.0, 5),
                        tuple(JudgedMetric.FILLER_RATIO, 0.5, 0),
                        tuple(JudgedMetric.CONTENT_INFO_UNIT_RATIO, null, 3),
                        tuple(JudgedMetric.WORDS_PER_UTTERANCE, null, 0));
        verify(notificationService).notifyCaregivers(ELDERLY_ID, NotificationType.PATTERN_CHANGE,
                "김철수님의 대화 모습", AssessmentLevel.MEDIUM.message());
    }

    @Test
    @DisplayName("위험도가 그대로거나 내려가면 알리지 않는다")
    void sameLevel_noNotification() {
        // given
        givenElderlyNotAssessed();
        givenBaselines();
        givenPrevious(5, 0, 0);
        givenDay(metric(DAY_START.plusSeconds(3600), 130.0, 100.0, 100.0, 100.0));

        // when
        assessmentService.assess(ELDERLY_ID, DATE);

        // then
        assertThat(savedAssessment().getLevel()).isEqualTo(AssessmentLevel.MEDIUM);
        verify(notificationService, never()).notifyCaregivers(any(), any(), any(), any());
    }

    @Test
    @DisplayName("첫 판정이 LOW면 알리지 않는다")
    void firstAssessment_low() {
        // given
        givenElderlyNotAssessed();
        givenBaselines();
        given(dailyAssessmentRepository.findFirstByElderlyIdAndAssessmentDateLessThanOrderByAssessmentDateDesc(
                ELDERLY_ID, DATE)).willReturn(Optional.empty());
        givenDay(metric(DAY_START.plusSeconds(3600), 130.0, 100.0, 100.0, 100.0));

        // when
        assessmentService.assess(ELDERLY_ID, DATE);

        // then
        DailyAssessment saved = savedAssessment();
        assertThat(saved.getLevel()).isEqualTo(AssessmentLevel.LOW);
        assertThat(saved.getMetrics()).extracting(MetricJudgement::streak).containsOnly(1, 0);
        verify(notificationService, never()).notifyCaregivers(any(), any(), any(), any());
    }

    @Test
    @DisplayName("그날 대화가 없었으면 판정을 남기지 않는다 (연속 횟수는 다음 판정에서 그대로 이어진다)")
    void noDataThatDay() {
        // given
        givenElderlyNotAssessed();
        givenBaselines();
        givenDay();

        // when
        assessmentService.assess(ELDERLY_ID, DATE);

        // then
        verify(dailyAssessmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("삭제된 노인은 판정하지 않는다")
    void deletedElderly() {
        // given
        given(elderlyRepository.findByIdAndDeletedFalse(ELDERLY_ID)).willReturn(Optional.empty());

        // when
        assessmentService.assess(ELDERLY_ID, DATE);

        // then
        verify(dailyAssessmentRepository, never()).save(any());
    }
}
