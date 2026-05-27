package io.github.ajmang.tdl.core.fixture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

public final class FixtureContextCollectorRegistry {

    private static final FixtureContextCollectorRegistry EMPTY = new FixtureContextCollectorRegistry(List.of());

    private final List<FixtureContextCollector> collectors;

    private FixtureContextCollectorRegistry(List<FixtureContextCollector> collectors) {
        this.collectors = List.copyOf(collectors);
    }

    public static FixtureContextCollectorRegistry empty() {
        return EMPTY;
    }

    public static FixtureContextCollectorRegistry of(List<? extends FixtureContextCollector> collectors) {
        Objects.requireNonNull(collectors, "collectors must not be null");
        List<FixtureContextCollector> ordered = new ArrayList<>(collectors.size());
        for (FixtureContextCollector collector : collectors) {
            ordered.add(Objects.requireNonNull(collector, "collector must not be null"));
        }
        ordered.sort(Comparator
                .comparingInt(FixtureContextCollector::order)
                .thenComparing(collector -> collector.getClass().getName()));
        return ordered.isEmpty() ? EMPTY : new FixtureContextCollectorRegistry(ordered);
    }

    public static FixtureContextCollectorRegistry load(ClassLoader classLoader) {
        ServiceLoader<FixtureContextCollector> loader = ServiceLoader.load(FixtureContextCollector.class, classLoader);
        List<FixtureContextCollector> discovered = loader.stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        return of(discovered);
    }

    public Map<String, Object> collect(FixtureContextCollectorInput input) {
        Objects.requireNonNull(input, "input must not be null");
        if (collectors.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        for (FixtureContextCollector collector : collectors) {
            if (!collector.supports(input.framework())) {
                continue;
            }
            Map<String, Object> contributed = collector.collect(input);
            if (contributed == null || contributed.isEmpty()) {
                continue;
            }
            mergeAttributes(attributes, contributed, collector.getClass().getName());
        }
        return attributes.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    private static void mergeAttributes(Map<String, Object> target, Map<String, Object> contributed, String sourceName) {
        for (Map.Entry<String, Object> entry : contributed.entrySet()) {
            String key = entry.getKey();
            if (target.containsKey(key)) {
                throw new IllegalStateException(
                        "Duplicate fixture context attribute key '" + key + "' contributed by " + sourceName);
            }
            target.put(key, entry.getValue());
        }
    }
}

