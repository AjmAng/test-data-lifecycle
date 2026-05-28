package io.github.ajmang.tdl.junit5examples.biztag;

import io.github.ajmang.tdl.core.fixture.FixtureContextCollector;
import io.github.ajmang.tdl.core.fixture.FixtureContextCollectorInput;

import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class BizTagFixtureContextCollector implements FixtureContextCollector {

    static final String BIZ_TAGS_KEY = "biz.tags";

    @Override
    public boolean supports(String framework) {
        return "junit5".equals(framework);
    }

    @Override
    public Map<String, Object> collect(FixtureContextCollectorInput input) {
        Set<String> tags = new LinkedHashSet<>();
        collectTags(input.testClass(), tags);
        collectTags(input.testMethod(), tags);
        if (tags.isEmpty()) {
            return Map.of();
        }
        return Map.of(BIZ_TAGS_KEY, Collections.unmodifiableSet(tags));
    }

    private void collectTags(AnnotatedElement element, Set<String> tags) {
        if (element == null) {
            return;
        }
        BizTag annotation = element.getAnnotation(BizTag.class);
        if (annotation == null) {
            return;
        }
        for (String value : annotation.value()) {
            String normalized = normalizeTag(value);
            if (!normalized.isEmpty()) {
                tags.add(normalized);
            }
        }
    }

    private String normalizeTag(String rawTag) {
        if (rawTag == null) {
            return "";
        }
        String value = rawTag.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}

