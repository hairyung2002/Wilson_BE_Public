package com.piuda.wilson.infrastructure.prompt;

import com.piuda.wilson.domain.conversation.entity.ConversationType;
import java.time.LocalTime;
import java.util.Map;

/**
 * 선제 대화 첫 안부 문장 (api-spec 6-2, 결정 J4). 푸시 알림 본문과 AI가 읽는 문장이 같다.
 *
 * <p>노인에게 그대로 보이고 들리는 문장이라 LLM이 아니라 BE가 정한다. 손주가 묻듯 짧게 묻고,
 * 기억을 시험하거나 지적하는 표현은 쓰지 않는다.</p>
 */
public final class ProactiveGreetingTemplate {

    private static final PromptTemplate MEAL = new PromptTemplate("{address}, 오늘 {meal}은 뭐 드셨어요?");
    private static final PromptTemplate MEDICATION =
            new PromptTemplate("{address}, {medicine} 드실 시간이에요. 챙겨 드셨어요?");
    private static final PromptTemplate WALK = new PromptTemplate("{address}, 오늘 산책은 다녀오셨어요?");
    private static final PromptTemplate DAILY = new PromptTemplate("{address}, 오늘은 기분이 어떠세요?");

    private static final LocalTime LUNCH_FROM = LocalTime.of(10, 30);
    private static final LocalTime DINNER_FROM = LocalTime.of(15, 0);
    private static final String DEFAULT_MEDICINE = "약";

    private ProactiveGreetingTemplate() {
    }

    /**
     * @param label       일정 라벨 (복약이면 약 이름). 없으면 null
     * @param addressTerm 호칭 (할머니·할아버지·어르신)
     * @param time        일정 시각 (식사 안부의 아침·점심·저녁)
     */
    public static String render(ConversationType type, String label, String addressTerm, LocalTime time) {
        return switch (type) {
            case MEAL -> MEAL.render(Map.of("address", addressTerm, "meal", mealOf(time)));
            case MEDICATION -> MEDICATION.render(Map.of("address", addressTerm, "medicine", medicineOf(label)));
            case WALK -> WALK.render(Map.of("address", addressTerm));
            default -> DAILY.render(Map.of("address", addressTerm));
        };
    }

    private static String mealOf(LocalTime time) {
        if (time.isBefore(LUNCH_FROM)) {
            return "아침";
        }
        return time.isBefore(DINNER_FROM) ? "점심" : "저녁";
    }

    private static String medicineOf(String label) {
        return label == null || label.isBlank() ? DEFAULT_MEDICINE : label.strip();
    }
}
