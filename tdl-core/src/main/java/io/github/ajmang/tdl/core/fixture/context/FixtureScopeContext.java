package io.github.ajmang.tdl.core.fixture.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record FixtureScopeContext(
        String scopeId,
        InjectionPoint injectionPoint,
        String injectionTarget,
        Integer parameterIndex,
        Map<String, Object> attributes
) {

    public static final String ATTR_ENGINE_RUN_ID = "tdl.engineRunId";
    public static final String ATTR_TEST_CLASS_NAME = "tdl.testClassName";
    public static final String ATTR_TEST_METHOD_NAME = "tdl.testMethodName";
    public static final String ATTR_THREAD_ID = "tdl.threadId";
    public static final String ATTR_TAGS = "tdl.tags";
    public static final String ATTR_ANNOTATIONS = "tdl.annotations";
    public static final String ATTR_PACKAGE_NAME = "tdl.packageName";
    private static final Set<String> SET_ATTRIBUTE_KEYS = Set.of(
            ATTR_TAGS,
            ATTR_ANNOTATIONS,
            "junit.tags",
            "junit.annotations",
            "testng.groups",
            "testng.annotations"
    );

    public FixtureScopeContext {
        attributes = freezeAttributes(attributes);
    }

    private static Map<String, Object> freezeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> frozen = new LinkedHashMap<>(attributes.size());
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            frozen.put(entry.getKey(), normalizeAttributeValue(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static Object normalizeAttributeValue(String key, Object value) {
        if (value instanceof Set<?> rawSet && SET_ATTRIBUTE_KEYS.contains(key)) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(rawSet));
        }
        return value;
    }

    public enum InjectionPoint {
        FIELD,
        PARAMETER
    }
}


