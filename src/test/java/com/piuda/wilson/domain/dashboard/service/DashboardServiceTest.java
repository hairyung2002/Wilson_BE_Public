package com.piuda.wilson.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

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
import com.piuda.wilson.domain.analysis.service.BaselineValue;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.ProactiveStatus;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.conversation.repository.ProactiveTriggerRepository;
import com.piuda.wilson.domain.dashboard.dto.DashboardPeriod;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.PointResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.TrendResponse;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.service.AccessActor;
import com.piuda.wilson.domain.elderly.service.ElderlyAccess;
import com.piuda.wilson.domain.elderly.service.ElderlyAccessService;
import com.piuda.wilson.global.error.CustomException;
import com.piuda.wilson.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 2026-10-20 15:00 KST */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T06:00:00Z"), KST);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final Long MEMBER_ID = 1L;
    private static final Long ELDERLY_ID = 15L;

    @Mock
    private ElderlyAccessService elderlyAccessService;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ProactiveTriggerRepository proactiveTriggerRepository;

    @Mock
    private DailyAssessmentRepository dailyAssessmentRepository;

    @Mock
    private ElderBaselineRepository elderBaselineRepository;

    @Mock
    private CognitiveMetricRepository cognitiveMetricRepository;

    private DashboardService dashboardService;
    private Elderly elderly;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(elderlyAccessService, conversationRepository,
                proactiveTriggerRepository, dailyAssessmentRepository, elderBaselineRepository,
                cognitiveMetricRepository, CLOCK);
        elderly = Elderly.builder().name("김철수").build();
        ReflectionTestUtils.setField(elderly, "id", ELDERLY_ID);
    }

    private void givenCaregiver() {
        given(elderlyAccessService.requireCaregiver(MEMBER_ID, ELDERLY_ID))
                .willReturn(new ElderlyAccess(elderly, AccessActor.MEMBER));
    }

    private Conversation conversation(long id, LocalDateTime startedAt, Integer minutes) {
        Conversation conversation = Conversation.builder().elderly(elderly).channel(ConversationChannel.VOICE)
                .type(ConversationType.MEAL).startedAt(startedAt).build();
        ReflectionTestUtils.setField(conversation, "id", id);
        if (minutes != null) {
            conversation.finish(startedAt.plusMinutes(minutes));
        }
        return conversation;
    }

    private DailyAssessment assessment(LocalDate date, int latencyStreak) {
        return DailyAssessment.builder().elderly(elderly).assessmentDate(date)
                .metrics(List.of(new MetricJudgement(JudgedMetric.SUBSEQUENT_ONSET_LATENCY, 2.1, latencyStreak)))
                .build();
    }

    private CognitiveMetric metric(LocalDateTime at, double latency) {
        return CognitiveMetric.builder().turnId(UUID.randomUUID().toString()).subjectId(elderly.getSubjectId())
                .analyzable(true)
                .metrics(new SpeechMetrics(null, null, null, null, null, null, latency, null, null, null, null))
                .analyzedAt(at.atZone(KST).toInstant()).build();
    }

    private void givenNoConversations() {
        given(conversationRepository.findByElderlyIdAndDeletedFalseOrderByStartedAtDescIdDesc(ELDERLY_ID, PageRequest.of(0, 3)))
                .willReturn(Page.empty());
    }

    private void givenNoAssessment() {
        given(dailyAssessmentRepository.findFirstByElderlyIdOrderByAssessmentDateDesc(ELDERLY_ID))
                .willReturn(Optional.empty());
    }

    private void givenNoActivity() {
        givenNoConversations();
        givenNoAssessment();
    }

    @Test
    @DisplayName("오늘(KST) 시작한 대화 수와 끝난 대화 시간 합, 오늘 선제 대화 응답·무응답 수를 보여 준다")
    void today() {
        // given
        givenCaregiver();
        givenNoAssessment();
        LocalDateTime midnight = TODAY.atStartOfDay();
        Conversation breakfast = conversation(15L, midnight.plusHours(8), 5);
        Conversation lunch = conversation(16L, midnight.plusHours(12), 7);
        Conversation now = conversation(17L, midnight.plusHours(14), null);
        given(conversationRepository.findByElderlyIdAndDeletedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                ELDERLY_ID, midnight, midnight.plusDays(1))).willReturn(List.of(breakfast, lunch, now));
        given(proactiveTriggerRepository.countByElderlyIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                ELDERLY_ID, ProactiveStatus.RESPONDED, midnight, midnight.plusDays(1))).willReturn(2L);
        given(proactiveTriggerRepository.countByElderlyIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                ELDERLY_ID, ProactiveStatus.MISSED, midnight, midnight.plusDays(1))).willReturn(1L);
        given(conversationRepository.findByElderlyIdAndDeletedFalseOrderByStartedAtDescIdDesc(ELDERLY_ID, PageRequest.of(0, 3)))
                .willReturn(new PageImpl<>(List.of(now, lunch, breakfast)));

        // when
        DashboardResponse response = dashboardService.getDashboard(MEMBER_ID, ELDERLY_ID, DashboardPeriod.WEEK);

        // then
        assertThat(response.today().conversationCount()).isEqualTo(3);
        assertThat(response.today().totalMinutes()).isEqualTo(12);
        assertThat(response.today().proactiveResponded()).isEqualTo(2);
        assertThat(response.today().proactiveMissed()).isEqualTo(1);
        assertThat(response.recentConversations()).extracting("conversationId").containsExactly(17L, 16L, 15L);
    }

    @Test
    @DisplayName("판정이 아직 없으면 COLLECTING과 '알아가는 중' 문구를 보여 주고, 기준값이 없는 지표는 baseline이 null이다")
    void collecting() {
        // given
        givenCaregiver();
        givenNoActivity();

        // when
        DashboardResponse response = dashboardService.getDashboard(MEMBER_ID, ELDERLY_ID, DashboardPeriod.WEEK);

        // then
        assertThat(response.assessment().level()).isEqualTo(AssessmentLevel.COLLECTING);
        assertThat(response.assessment().message()).isEqualTo(AssessmentLevel.COLLECTING.message());
        assertThat(response.assessment().since()).isNull();
        assertThat(response.trends()).extracting(TrendResponse::metric).containsExactly(
                "subsequent_onset_latency_ms", "filler_ratio", "content_info_unit_ratio", "words_per_utterance");
        assertThat(response.trends()).allSatisfy(trend -> {
            assertThat(trend.baseline()).isNull();
            assertThat(trend.points()).isEmpty();
        });
    }

    @Test
    @DisplayName("지금 위험도와, 그 위험도가 시작된 첫 판정일(since)을 보여 준다")
    void assessmentSince() {
        // given
        givenCaregiver();
        givenNoConversations();
        given(dailyAssessmentRepository.findFirstByElderlyIdOrderByAssessmentDateDesc(ELDERLY_ID))
                .willReturn(Optional.of(assessment(LocalDate.of(2026, 10, 19), 6)));
        given(dailyAssessmentRepository.findFirstByElderlyIdAndLevelNotOrderByAssessmentDateDesc(
                ELDERLY_ID, AssessmentLevel.MEDIUM)).willReturn(Optional.of(assessment(LocalDate.of(2026, 10, 15), 4)));
        given(dailyAssessmentRepository.findFirstByElderlyIdAndAssessmentDateGreaterThanOrderByAssessmentDateAsc(
                ELDERLY_ID, LocalDate.of(2026, 10, 15))).willReturn(Optional.of(assessment(LocalDate.of(2026, 10, 17), 5)));

        // when
        DashboardResponse response = dashboardService.getDashboard(MEMBER_ID, ELDERLY_ID, DashboardPeriod.WEEK);

        // then
        assertThat(response.assessment().level()).isEqualTo(AssessmentLevel.MEDIUM);
        assertThat(response.assessment().message()).isEqualTo(AssessmentLevel.MEDIUM.message());
        assertThat(response.assessment().since()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    @DisplayName("처음부터 같은 위험도였으면 since는 첫 판정일이다")
    void assessmentSince_fromFirst() {
        // given
        givenCaregiver();
        givenNoConversations();
        given(dailyAssessmentRepository.findFirstByElderlyIdOrderByAssessmentDateDesc(ELDERLY_ID))
                .willReturn(Optional.of(assessment(LocalDate.of(2026, 10, 19), 0)));
        given(dailyAssessmentRepository.findFirstByElderlyIdAndLevelNotOrderByAssessmentDateDesc(
                ELDERLY_ID, AssessmentLevel.LOW)).willReturn(Optional.empty());
        given(dailyAssessmentRepository.findFirstByElderlyIdOrderByAssessmentDateAsc(ELDERLY_ID))
                .willReturn(Optional.of(assessment(LocalDate.of(2026, 9, 20), 0)));

        // when & then
        assertThat(dashboardService.getDashboard(MEMBER_ID, ELDERLY_ID, DashboardPeriod.WEEK).assessment().since())
                .isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    @DisplayName("기간(WEEK: 오늘 포함 7일) 동안 지표별 일별 평균을 날짜순으로, 기준값과 함께 보여 준다")
    void trends() {
        // given
        givenCaregiver();
        givenNoActivity();
        given(elderBaselineRepository.findByElderlyId(ELDERLY_ID)).willReturn(List.of(ElderBaseline.of(elderly,
                JudgedMetric.SUBSEQUENT_ONSET_LATENCY,
                new BaselineValue(820.0, 110.0, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 14)))));
        LocalDateTime from = TODAY.minusDays(6).atStartOfDay();
        given(cognitiveMetricRepository.findBySubjectIdAndAnalyzableTrueAndAnalyzedAtGreaterThanEqualAndAnalyzedAtLessThan(
                elderly.getSubjectId(), from.atZone(KST).toInstant(), TODAY.plusDays(1).atStartOfDay(KST).toInstant()))
                .willReturn(List.of(metric(TODAY.atTime(9, 0), 800.0), metric(TODAY.atTime(10, 0), 900.0),
                        metric(from.plusHours(9), 790.0)));

        // when
        DashboardResponse response = dashboardService.getDashboard(MEMBER_ID, ELDERLY_ID, DashboardPeriod.WEEK);

        // then
        TrendResponse latency = response.trends().get(0);
        assertThat(latency.label()).isEqualTo("대답까지 걸리는 시간");
        assertThat(latency.unit()).isEqualTo("ms");
        assertThat(latency.baseline().mean()).isEqualTo(820.0);
        assertThat(latency.baseline().std()).isEqualTo(110.0);
        assertThat(latency.points()).extracting(PointResponse::date, PointResponse::value)
                .containsExactly(tuple(TODAY.minusDays(6), 790.0), tuple(TODAY, 850.0));
        assertThat(response.trends().get(1).baseline()).isNull();
    }

    @Test
    @DisplayName("노인 본인이나 연결되지 않은 보호자는 볼 수 없다")
    void forbidden() {
        // given
        given(elderlyAccessService.requireCaregiver(MEMBER_ID, ELDERLY_ID))
                .willThrow(new CustomException(ErrorCode.NO_PERMISSION));

        // when & then
        assertThatThrownBy(() -> dashboardService.getDashboard(MEMBER_ID, ELDERLY_ID, DashboardPeriod.MONTH))
                .isInstanceOf(CustomException.class);
    }
}
