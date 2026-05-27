package io.github.ajmang.tdl.core.fixture;

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
}

