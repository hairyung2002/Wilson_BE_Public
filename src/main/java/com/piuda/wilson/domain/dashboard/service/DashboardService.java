package com.piuda.wilson.domain.dashboard.service;

import com.piuda.wilson.domain.analysis.entity.AssessmentLevel;
import com.piuda.wilson.domain.analysis.entity.DailyAssessment;
import com.piuda.wilson.domain.analysis.entity.ElderBaseline;
import com.piuda.wilson.domain.analysis.entity.JudgedMetric;
import com.piuda.wilson.domain.analysis.repository.CognitiveMetricRepository;
import com.piuda.wilson.domain.analysis.repository.DailyAssessmentRepository;
import com.piuda.wilson.domain.analysis.repository.ElderBaselineRepository;
import com.piuda.wilson.domain.analysis.service.MetricDailyMeans;
import com.piuda.wilson.domain.conversation.dto.ConversationSummaryResponse;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ProactiveStatus;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.conversation.repository.ProactiveTriggerRepository;
import com.piuda.wilson.domain.dashboard.dto.DashboardPeriod;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.AssessmentStatusResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.BaselineResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.PointResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.TodayResponse;
import com.piuda.wilson.domain.dashboard.dto.DashboardResponse.TrendResponse;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.service.ElderlyAccessService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 보호자 대시보드 (api-spec 13). 연결된 보호자만 볼 수 있다. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int RECENT_CONVERSATIONS = 3;

    private final ElderlyAccessService elderlyAccessService;
    private final ConversationRepository conversationRepository;
    private final ProactiveTriggerRepository proactiveTriggerRepository;
    private final DailyAssessmentRepository dailyAssessmentRepository;
    private final ElderBaselineRepository elderBaselineRepository;
    private final CognitiveMetricRepository cognitiveMetricRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long memberId, Long elderlyId, DashboardPeriod period) {
        Elderly elderly = elderlyAccessService.requireCaregiver(memberId, elderlyId).elderly();
        LocalDate today = LocalDate.now(clock);
        List<ConversationSummaryResponse> recent = conversationRepository
                .findByElderlyIdAndDeletedFalseOrderByStartedAtDescIdDesc(elderlyId, PageRequest.of(0, RECENT_CONVERSATIONS))
                .map(ConversationSummaryResponse::from).getContent();
        return new DashboardResponse(today(elderlyId, today), assessment(elderlyId), recent,
                trends(elderly, today, period));
    }

    private TodayResponse today(Long elderlyId, LocalDate today) {
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        List<Conversation> conversations = conversationRepository
                .findByElderlyIdAndDeletedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(elderlyId, from, to);
        return new TodayResponse(conversations.size(),
                conversations.stream().mapToLong(Conversation::minutesTalked).sum(),
                countProactive(elderlyId, ProactiveStatus.RESPONDED, from, to),
                countProactive(elderlyId, ProactiveStatus.MISSED, from, to));
    }

    private long countProactive(Long elderlyId, ProactiveStatus status, LocalDateTime from, LocalDateTime to) {
        return proactiveTriggerRepository.countByElderlyIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                elderlyId, status, from, to);
    }

    private AssessmentStatusResponse assessment(Long elderlyId) {
        Optional<DailyAssessment> latest = dailyAssessmentRepository.findFirstByElderlyIdOrderByAssessmentDateDesc(
                elderlyId);
        if (latest.isEmpty()) {
            AssessmentLevel collecting = AssessmentLevel.COLLECTING;
            return new AssessmentStatusResponse(collecting, collecting.message(), null);
        }
        AssessmentLevel level = latest.get().getLevel();
        return new AssessmentStatusResponse(level, level.message(), since(elderlyId, level));
    }

    /** 지금과 다른 위험도였던 마지막 판정 다음의 첫 판정일. 처음부터 같았으면 첫 판정일. */
    private LocalDate since(Long elderlyId, AssessmentLevel level) {
        Optional<DailyAssessment> lastDifferent = dailyAssessmentRepository
                .findFirstByElderlyIdAndLevelNotOrderByAssessmentDateDesc(elderlyId, level);
        Optional<DailyAssessment> start = lastDifferent.isPresent()
                ? dailyAssessmentRepository.findFirstByElderlyIdAndAssessmentDateGreaterThanOrderByAssessmentDateAsc(
                        elderlyId, lastDifferent.get().getAssessmentDate())
                : dailyAssessmentRepository.findFirstByElderlyIdOrderByAssessmentDateAsc(elderlyId);
        return start.map(DailyAssessment::getAssessmentDate).orElse(null);
    }

    private List<TrendResponse> trends(Elderly elderly, LocalDate today, DashboardPeriod period) {
        ZoneId zone = clock.getZone();
        Map<JudgedMetric, ElderBaseline> baselines = new EnumMap<>(JudgedMetric.class);
        elderBaselineRepository.findByElderlyId(elderly.getId())
                .forEach(baseline -> baselines.put(baseline.getMetric(), baseline));
        MetricDailyMeans means = MetricDailyMeans.of(cognitiveMetricRepository
                .findBySubjectIdAndAnalyzableTrueAndAnalyzedAtGreaterThanEqualAndAnalyzedAtLessThan(
                        elderly.getSubjectId(), today.minusDays(period.days() - 1L).atStartOfDay(zone).toInstant(),
                        today.plusDays(1).atStartOfDay(zone).toInstant()), zone);
        return Arrays.stream(JudgedMetric.values())
                .map(metric -> new TrendResponse(metric.code(), metric.label(), metric.unit(),
                        Optional.ofNullable(baselines.get(metric))
                                .map(baseline -> new BaselineResponse(baseline.getMean(), baseline.getStd()))
                                .orElse(null),
                        means.meansFor(metric).entrySet().stream()
                                .map(point -> new PointResponse(point.getKey(), point.getValue())).toList()))
                .toList();
    }
}
