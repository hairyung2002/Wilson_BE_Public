package com.piuda.wilson.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.piuda.wilson.domain.analysis.entity.AssessmentLevel;
import com.piuda.wilson.domain.analysis.entity.CognitiveMetric;
import com.piuda.wilson.domain.analysis.entity.DailyAssessment;
import com.piuda.wilson.domain.analysis.entity.ElderBaseline;
import com.piuda.wilson.domain.analysis.entity.JudgedMetric;
import com.piuda.wilson.domain.analysis.entity.SpeechMetrics;
import com.piuda.wilson.domain.analysis.repository.CognitiveMetricRepository;
import com.piuda.wilson.domain.analysis.repository.DailyAssessmentRepository;
import com.piuda.wilson.domain.analysis.repository.ElderBaselineRepository;
import com.piuda.wilson.domain.analysis.service.AssessmentService;
import com.piuda.wilson.domain.caregiver.entity.CareRelationship;
import com.piuda.wilson.domain.caregiver.entity.CareRole;
import com.piuda.wilson.domain.caregiver.entity.Caregiver;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.caregiver.repository.CaregiverRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import com.piuda.wilson.domain.member.entity.Member;
import com.piuda.wilson.domain.member.entity.MemberType;
import com.piuda.wilson.domain.member.repository.MemberRepository;
import com.piuda.wilson.domain.notification.entity.Notification;
import com.piuda.wilson.domain.notification.entity.NotificationType;
import com.piuda.wilson.domain.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/** 결정 D: 처음 14일로 기준값을 만들고, 그 뒤 5일 연속 벗어난 지표가 생기면 위험도를 올리고 OWNER에게 알린다. */
@SpringBootTest
@Transactional
class AssessmentFlowIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate FIRST = LocalDate.of(2026, 9, 1);

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CaregiverRepository caregiverRepository;

    @Autowired
    private ElderlyRepository elderlyRepository;

    @Autowired
    private CareRelationshipRepository careRelationshipRepository;

    @Autowired
    private CognitiveMetricRepository cognitiveMetricRepository;

    @Autowired
    private ElderBaselineRepository elderBaselineRepository;

    @Autowired
    private DailyAssessmentRepository dailyAssessmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    private void talk(Elderly elderly, LocalDate day, double latency) {
        cognitiveMetricRepository.save(CognitiveMetric.builder().turnId(UUID.randomUUID().toString())
                .subjectId(elderly.getSubjectId()).analyzable(true)
                .metrics(new SpeechMetrics(40, 3, 0.1, 0.05, 0.5, 6.0, latency, 0.2, null, null, null))
                .analyzedAt(day.atTime(10, 0).atZone(KST).toInstant()).build());
    }

    private void assess(Elderly elderly, LocalDate day) {
        assessmentService.assess(elderly.getId(), day);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("기준값 생성 → 대답 시간이 5일 연속 늘면 MEDIUM 판정과 OWNER 알림, 대화 없는 날은 연속 횟수를 유지한다")
    void assessmentFlow() {
        // given: 보호자(OWNER)와 노인
        Member owner = memberRepository.save(Member.builder().email("owner@wilson.com").password("x").name("아들")
                .memberType(MemberType.CAREGIVER).build());
        Caregiver caregiver = caregiverRepository.save(Caregiver.builder().member(owner).build());
        Elderly elderly = elderlyRepository.save(Elderly.builder().name("김철수").build());
        careRelationshipRepository.save(CareRelationship.builder().caregiver(caregiver).elderly(elderly)
                .relation("아들").role(CareRole.OWNER).build());
        for (int day = 0; day < 14; day++) {
            talk(elderly, FIRST.plusDays(day), day % 2 == 0 ? 800.0 : 900.0);
        }

        // when: 14일째 기준값을 만든다
        assess(elderly, FIRST.plusDays(13));

        // then
        assertThat(elderBaselineRepository.findByElderlyId(elderly.getId()))
                .extracting(ElderBaseline::getMetric, ElderBaseline::getMean)
                .contains(tuple(JudgedMetric.SUBSEQUENT_ONSET_LATENCY, 850.0));
        assertThat(dailyAssessmentRepository.findAll()).isEmpty();

        // when: 이후 대답 시간이 크게 늘어난 날이 4일, 대화 없는 하루, 다시 늘어난 하루
        LocalDate day = FIRST.plusDays(14);
        for (int i = 0; i < 4; i++, day = day.plusDays(1)) {
            talk(elderly, day, 1200.0);
            assess(elderly, day);
        }
        LocalDate quietDay = day;
        assess(elderly, quietDay);
        LocalDate fifthDay = quietDay.plusDays(1);
        talk(elderly, fifthDay, 1200.0);
        assess(elderly, fifthDay);

        // then
        List<DailyAssessment> assessments = dailyAssessmentRepository
                .findByElderlyIdAndAssessmentDateBetweenOrderByAssessmentDateAsc(elderly.getId(), FIRST, fifthDay);
        assertThat(assessments).extracting(DailyAssessment::getAssessmentDate).doesNotContain(quietDay).hasSize(5);
        assertThat(assessments).extracting(DailyAssessment::getLevel).containsExactly(AssessmentLevel.LOW,
                AssessmentLevel.LOW, AssessmentLevel.LOW, AssessmentLevel.LOW, AssessmentLevel.MEDIUM);
        assertThat(assessments.get(4).flaggedMetrics()).containsExactly(JudgedMetric.SUBSEQUENT_ONSET_LATENCY);
        List<Notification> notifications = notificationRepository
                .findByRecipientIdOrderByIdDesc(owner.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(notifications).extracting(Notification::getType, Notification::getBody)
                .containsExactly(tuple(NotificationType.PATTERN_CHANGE,
                        AssessmentLevel.MEDIUM.message()));
    }
}
