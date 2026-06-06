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

    @Deprecated(forRemoval = false)
    public FixtureScopeContext(
            String engineRunId,
            String testClassName,
            String testMethodName,
            String junitUniqueId,
            InjectionPoint injectionPoint,
            String injectionTarget,
            Integer parameterIndex,
            long threadId,
            Set<String> tags,
            Set<String> annotations,
            String packageName,
            Map<String, Object> attributes
    ) {
        this(
                junitUniqueId,
                injectionPoint,
                injectionTarget,
                parameterIndex,
                mergeLegacyAttributes(
                        engineRunId,
                        testClassName,
                        testMethodName,
                        threadId,
                        tags,
                        annotations,
                        packageName,
                        attributes
                )
        );
    }

    @Deprecated(forRemoval = false)
    public String engineRunId() {
        return asString(attributes.get(ATTR_ENGINE_RUN_ID));
    }

    @Deprecated(forRemoval = false)
    public String testClassName() {
        return asString(attributes.get(ATTR_TEST_CLASS_NAME));
    }

    @Deprecated(forRemoval = false)
    public String testMethodName() {
        return asString(attributes.get(ATTR_TEST_METHOD_NAME));
    }

    @Deprecated(forRemoval = false)
    public String junitUniqueId() {
        return scopeId;
    }

    @Deprecated(forRemoval = false)
    public long threadId() {
        Object value = attributes.get(ATTR_THREAD_ID);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return -1L;
    }

    @Deprecated(forRemoval = false)
    public Set<String> tags() {
        return readStringSet(ATTR_TAGS, "junit.tags", "testng.groups");
    }

    @Deprecated(forRemoval = false)
    public Set<String> annotations() {
        return readStringSet(ATTR_ANNOTATIONS, "junit.annotations", "testng.annotations");
    }

    @Deprecated(forRemoval = false)
    public String packageName() {
        Object value = attributes.get(ATTR_PACKAGE_NAME);
        if (value == null) {
            value = attributes.get("junit.packageName");
        }
        if (value == null) {
            value = attributes.get("testng.packageName");
        }
        return asString(value);
    }

    private static Map<String, Object> mergeLegacyAttributes(
            String engineRunId,
            String testClassName,
            String testMethodName,
            long threadId,
            Set<String> tags,
            Set<String> annotations,
            String packageName,
            Map<String, Object> attributes
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (attributes != null) {
            merged.putAll(attributes);
        }
        putIfAbsent(merged, ATTR_ENGINE_RUN_ID, engineRunId);
        putIfAbsent(merged, ATTR_TEST_CLASS_NAME, testClassName);
        putIfAbsent(merged, ATTR_TEST_METHOD_NAME, testMethodName);
        putIfAbsent(merged, ATTR_THREAD_ID, threadId);
        putIfAbsent(merged, ATTR_TAGS, copyStringSet(tags));
        putIfAbsent(merged, ATTR_ANNOTATIONS, copyStringSet(annotations));
        putIfAbsent(merged, ATTR_PACKAGE_NAME, packageName);
        return merged;
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

    private static void putIfAbsent(Map<String, Object> attributes, String key, Object value) {
        if (value == null) {
            return;
        }
        attributes.putIfAbsent(key, value);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Set<String> readStringSet(String... candidateKeys) {
        for (String key : candidateKeys) {
            Object value = attributes.get(key);
            if (value != null) {
                return coerceStringSet(value);
            }
        }
        return Set.of();
    }

    private static Set<String> copyStringSet(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    private static Set<String> coerceStringSet(Object source) {
        if (!(source instanceof Set<?> rawSet)) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (Object item : rawSet) {
            values.add(String.valueOf(item));
        }
        return Collections.unmodifiableSet(values);
    }


    public enum InjectionPoint {
        FIELD,
        PARAMETER
    }
}

