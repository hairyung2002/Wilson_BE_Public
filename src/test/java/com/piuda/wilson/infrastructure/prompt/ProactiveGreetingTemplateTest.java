package com.piuda.wilson.infrastructure.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.domain.conversation.entity.ConversationType;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProactiveGreetingTemplateTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "07:00, 아침",
            "10:29, 아침",
            "10:30, 점심",
            "14:59, 점심",
            "15:00, 저녁",
            "19:00, 저녁"
    })
    @DisplayName("식사 안부는 시각에 따라 아침(10:30 전)·점심(15:00 전)·저녁을 묻는다")
    void meal(LocalTime time, String meal) {
        // when
        String greeting = ProactiveGreetingTemplate.render(ConversationType.MEAL, null, "할머니", time);

        // then
        assertThat(greeting).isEqualTo("할머니, 오늘 " + meal + "은 뭐 드셨어요?");
    }

    @Test
    @DisplayName("복약 안부는 일정 라벨(약 이름)을 쓰고, 없으면 '약'이라고 한다")
    void medication() {
        // when
        String labeled = ProactiveGreetingTemplate.render(ConversationType.MEDICATION, "혈압약", "할아버지",
                LocalTime.of(9, 0));
        String unlabeled = ProactiveGreetingTemplate.render(ConversationType.MEDICATION, " ", "어르신",
                LocalTime.of(9, 0));

        // then
        assertThat(labeled).isEqualTo("할아버지, 혈압약 드실 시간이에요. 챙겨 드셨어요?");
        assertThat(unlabeled).isEqualTo("어르신, 약 드실 시간이에요. 챙겨 드셨어요?");
    }

    @Test
    @DisplayName("산책·하루 안부는 정해진 문장을 쓰고, 그 밖의 유형은 하루 안부로 묻는다")
    void walkAndDaily() {
        // given
        LocalTime time = LocalTime.of(16, 0);

        // when & then
        assertThat(ProactiveGreetingTemplate.render(ConversationType.WALK, "공원", "할머니", time))
                .isEqualTo("할머니, 오늘 산책은 다녀오셨어요?");
        assertThat(ProactiveGreetingTemplate.render(ConversationType.DAILY, null, "할머니", time))
                .isEqualTo("할머니, 오늘은 기분이 어떠세요?");
        assertThat(ProactiveGreetingTemplate.render(ConversationType.FREE, null, "할머니", time))
                .isEqualTo("할머니, 오늘은 기분이 어떠세요?");
    }
}
