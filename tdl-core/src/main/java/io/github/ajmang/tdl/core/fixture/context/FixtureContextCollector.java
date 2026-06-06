package io.github.ajmang.tdl.core.fixture.context;

import java.util.Map;

/**
 * Collects additional strategy-facing context attributes from a framework-specific runtime context.
 */
public interface FixtureContextCollector {

    /**
     * Returns whether this collector should run for the given framework id, for example {@code junit5} or {@code testng}.
     */
    default boolean supports(String framework) {
        return true;
    }

    /**
     * Lower values run first.
     */
    default int order() {
        return 0;
    }

    /**
     * Collects additional attributes to merge into {@link FixtureScopeContext#attributes()}.
     */
    Map<String, Object> collect(FixtureContextCollectorInput input);
}


