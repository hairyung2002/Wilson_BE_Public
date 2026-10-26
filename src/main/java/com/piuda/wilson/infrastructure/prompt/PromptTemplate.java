package com.piuda.wilson.infrastructure.prompt;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PromptTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final String template;

    public PromptTemplate(String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("프롬프트 템플릿은 비어 있을 수 없습니다.");
        }
        this.template = template;
    }

    public String render(Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                throw new IllegalArgumentException("치환할 변수가 없습니다: " + key);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
