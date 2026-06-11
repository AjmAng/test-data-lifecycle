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
        sourceAttributes.put(FixtureScopeContext.ATTR_TAGS, sourceTags);
        sourceAttributes.put(FixtureScopeContext.ATTR_ANNOTATIONS, sourceAnnotations);

        FixtureScopeContext context = new FixtureScopeContext(
                "run-1",
                FixtureScopeContext.InjectionPoint.FIELD,
                "field",
                null,
                sourceAttributes
        );

        sourceTags.add("tag-c");
        sourceAnnotations.add("ann-c");
        sourceAttributes.put("late", "value");

        Assertions.assertEquals(new LinkedHashSet<>(List.of("tag-a", "tag-b")), context.attributes().get(FixtureScopeContext.ATTR_TAGS));
        Assertions.assertEquals(new LinkedHashSet<>(List.of("ann-a", "ann-b")), context.attributes().get(FixtureScopeContext.ATTR_ANNOTATIONS));
        Assertions.assertEquals("junit5", context.attributes().get("framework"));
        Assertions.assertFalse(context.attributes().containsKey("late"));

        @SuppressWarnings("unchecked")
        Set<String> tags = (Set<String>) context.attributes().get(FixtureScopeContext.ATTR_TAGS);
        @SuppressWarnings("unchecked")
        Set<String> annotations = (Set<String>) context.attributes().get(FixtureScopeContext.ATTR_ANNOTATIONS);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> tags.add("tag-d"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> annotations.add("ann-d"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> context.attributes().put("x", "y"));
    }

    @Test
    void attributesAreAccessibleViaRecordComponents() {
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

        Assertions.assertEquals("scope-1", context.scopeId());
        Assertions.assertEquals(FixtureScopeContext.InjectionPoint.PARAMETER, context.injectionPoint());
        Assertions.assertEquals("arg0", context.injectionTarget());
        Assertions.assertEquals(0, context.parameterIndex());
        Assertions.assertEquals("run-2", context.attributes().get(FixtureScopeContext.ATTR_ENGINE_RUN_ID));
        Assertions.assertEquals("example.TestClass", context.attributes().get(FixtureScopeContext.ATTR_TEST_CLASS_NAME));
        Assertions.assertEquals("testMethod", context.attributes().get(FixtureScopeContext.ATTR_TEST_METHOD_NAME));
        Assertions.assertEquals(7L, context.attributes().get(FixtureScopeContext.ATTR_THREAD_ID));
        Assertions.assertEquals(new LinkedHashSet<>(List.of("g1", "g2")), context.attributes().get(FixtureScopeContext.ATTR_TAGS));
        Assertions.assertEquals(new LinkedHashSet<>(List.of("a1", "a2")), context.attributes().get(FixtureScopeContext.ATTR_ANNOTATIONS));
        Assertions.assertEquals("example", context.attributes().get(FixtureScopeContext.ATTR_PACKAGE_NAME));
    }
}
