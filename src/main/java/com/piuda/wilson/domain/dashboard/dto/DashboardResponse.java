package com.piuda.wilson.domain.dashboard.dto;

import com.piuda.wilson.domain.analysis.entity.AssessmentLevel;
import com.piuda.wilson.domain.conversation.dto.ConversationSummaryResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * 보호자 대시보드 (api-spec 13). recentConversations 항목은 대화 목록(6-9)과 같은 형식이다.
 * 판정 문구는 보호자에게 보이는 그대로이며 level 값은 화면에 쓰지 않는다 (결정 D5).
 */
public record DashboardResponse(TodayResponse today, AssessmentStatusResponse assessment,
                                List<ConversationSummaryResponse> recentConversations, List<TrendResponse> trends) {

    /** 오늘(KST) 시작한 대화 기준. totalMinutes는 끝난 대화 길이의 합. */
    public record TodayResponse(int conversationCount, long totalMinutes, long proactiveResponded,
                                long proactiveMissed) {
    }

    /** since: 지금 위험도로 처음 판정된 날. 판정 전(COLLECTING)이면 null. */
    public record AssessmentStatusResponse(AssessmentLevel level, String message, LocalDate since) {
    }

    /** baseline: 기준값이 아직 없으면 null. points: 데이터가 있는 날만. */
    public record TrendResponse(String metric, String label, String unit, BaselineResponse baseline,
                                List<PointResponse> points) {
    }

    public record BaselineResponse(double mean, double std) {
    }

    public record PointResponse(LocalDate date, double value) {
    }
}
