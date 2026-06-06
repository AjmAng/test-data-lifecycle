package io.github.ajmang.tdl.core.fixture;

import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class FixtureScopeContextTest {

    @Test
    void copiesAndFreezesCollections() {
        Set<String> sourceTags = new LinkedHashSet<>(List.of("tag-a", "tag-b"));
        Set<String> sourceAnnotations = new LinkedHashSet<>(List.of("ann-a", "ann-b"));
        Map<String, Object> sourceAttributes = new LinkedHashMap<>();
        sourceAttributes.put("framework", "junit5");
        sourceAttributes.put("custom.key", 42);

        FixtureScopeContext context = new FixtureScopeContext(
                "run-1",
                "example.TestClass",
                "testMethod",
                "uid-1",
                FixtureScopeContext.InjectionPoint.FIELD,
                "field",
                null,
                99L,
                sourceTags,
                sourceAnnotations,
                "example",
                sourceAttributes
        );

        sourceTags.add("tag-c");
        sourceAnnotations.add("ann-c");
        sourceAttributes.put("late", "value");

        Assertions.assertEquals(new LinkedHashSet<>(List.of("tag-a", "tag-b")), context.tags());
        Assertions.assertEquals(new LinkedHashSet<>(List.of("ann-a", "ann-b")), context.annotations());
        Assertions.assertEquals("junit5", context.attributes().get("framework"));
        Assertions.assertFalse(context.attributes().containsKey("late"));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> context.tags().add("tag-d"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> context.annotations().add("ann-d"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> context.attributes().put("x", "y"));
    }

    @Test
    void legacyAccessorsReadFromAttributesInLeanContext() {
        Set<String> tags = new LinkedHashSet<>(List.of("g1", "g2"));
        Set<String> annotations = new LinkedHashSet<>(List.of("a1", "a2"));
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FixtureScopeContext.ATTR_ENGINE_RUN_ID, "run-2");
        attributes.put(FixtureScopeContext.ATTR_TEST_CLASS_NAME, "example.TestClass");
        attributes.put(FixtureScopeContext.ATTR_TEST_METHOD_NAME, "testMethod");
        attributes.put(FixtureScopeContext.ATTR_THREAD_ID, 7L);
        attributes.put(FixtureScopeContext.ATTR_TAGS, tags);
        attributes.put(FixtureScopeContext.ATTR_ANNOTATIONS, annotations);
        attributes.put(FixtureScopeContext.ATTR_PACKAGE_NAME, "example");

        FixtureScopeContext context = new FixtureScopeContext(
                "scope-1",
                FixtureScopeContext.InjectionPoint.PARAMETER,
                "arg0",
                0,
                attributes
        );

        tags.add("g3");
        annotations.add("a3");

        Assertions.assertEquals("scope-1", context.scopeId());
        Assertions.assertEquals("scope-1", context.junitUniqueId());
        Assertions.assertEquals("run-2", context.engineRunId());
        Assertions.assertEquals("example.TestClass", context.testClassName());
        Assertions.assertEquals("testMethod", context.testMethodName());
        Assertions.assertEquals(7L, context.threadId());
        Assertions.assertEquals(new LinkedHashSet<>(List.of("g1", "g2")), context.tags());
        Assertions.assertEquals(new LinkedHashSet<>(List.of("a1", "a2")), context.annotations());
        Assertions.assertEquals("example", context.packageName());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> context.tags().add("g4"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> context.annotations().add("a4"));
    }
}

