package com.piuda.wilson.infrastructure.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptTemplateTest {

    @Test
    @DisplayName("플레이스홀더를 전달된 변수로 치환한다")
    void render_replacesPlaceholders() {
        // given
        PromptTemplate template = new PromptTemplate("안녕하세요 {name}님, {meal} 드셨어요?");

        // when
        String result = template.render(Map.of("name", "홍길동", "meal", "점심"));

        // then
        assertThat(result).isEqualTo("안녕하세요 홍길동님, 점심 드셨어요?");
    }

    @Test
    @DisplayName("치환할 변수가 없으면 예외를 던진다 (Fail Fast)")
    void render_missingVariable_throws() {
        // given
        PromptTemplate template = new PromptTemplate("안녕하세요 {name}님");

        // when & then
        assertThatThrownBy(() -> template.render(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("빈 템플릿으로 생성할 수 없다")
    void constructor_blankTemplate_throws() {
        // when & then
        assertThatThrownBy(() -> new PromptTemplate(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
