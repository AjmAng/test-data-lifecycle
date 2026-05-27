package io.github.ajmang.tdl.core.fixture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class FixtureContextCollectorRegistryTest {

    @Test
    void collectsOrderedAttributesFromSupportingCollectors() {
        FixtureContextCollector first = new NamedCollector("second", 10, true, Map.of("b", 2));
        FixtureContextCollector second = new NamedCollector("first", 0, true, Map.of("a", 1));
        FixtureContextCollector ignored = new NamedCollector("ignored", 5, false, Map.of("c", 3));

        FixtureContextCollectorRegistry registry = FixtureContextCollectorRegistry.of(List.of(first, second, ignored));

        Map<String, Object> attributes = registry.collect(sampleInput());

        Assertions.assertEquals(List.of("a", "b"), List.copyOf(attributes.keySet()));
        Assertions.assertEquals(1, attributes.get("a"));
        Assertions.assertEquals(2, attributes.get("b"));
    }

    @Test
    void rejectsDuplicateAttributeKeys() {
        FixtureContextCollector first = new NamedCollector("first", 0, true, Map.of("dup", 1));
        FixtureContextCollector second = new NamedCollector("second", 1, true, Map.of("dup", 2));
        FixtureContextCollectorRegistry registry = FixtureContextCollectorRegistry.of(List.of(first, second));

        IllegalStateException exception = Assertions.assertThrows(
                IllegalStateException.class,
                () -> registry.collect(sampleInput())
        );

        Assertions.assertTrue(exception.getMessage().contains("dup"));
    }

    @Test
    void emptyRegistryReturnsEmptyAttributes() {
        Assertions.assertEquals(Map.of(), FixtureContextCollectorRegistry.empty().collect(sampleInput()));
    }

    @Test
    void exposesTypedFrameworkContextHelper() {
        FixtureContextCollectorInput input = sampleInput();

        Assertions.assertEquals("raw-context", input.frameworkContext(String.class).orElseThrow());
        Assertions.assertTrue(input.frameworkContext(Integer.class).isEmpty());
    }

    private FixtureContextCollectorInput sampleInput() {
        return new FixtureContextCollectorInput(
                "junit5",
                "raw-context",
                FixtureContextCollectorRegistryTest.class,
                null,
                FixtureScopeContext.InjectionPoint.FIELD,
                "fixtureField",
                null
        );
    }

    private record NamedCollector(String name, int order, boolean supported, Map<String, Object> attributes)
            implements FixtureContextCollector {

        @Override
        public boolean supports(String framework) {
            return supported && "junit5".equals(framework);
        }

        @Override
        public Map<String, Object> collect(FixtureContextCollectorInput input) {
            return attributes;
        }
    }
}

