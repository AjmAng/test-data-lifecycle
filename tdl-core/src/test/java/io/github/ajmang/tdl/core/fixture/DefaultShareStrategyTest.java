package io.github.ajmang.tdl.core.fixture;

import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;
import io.github.ajmang.tdl.core.fixture.share.DefaultShareStrategy;
import io.github.ajmang.tdl.core.fixture.share.ShareStrategy;
import io.github.ajmang.tdl.core.fixture.support.CountingProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class DefaultShareStrategyTest {

    private final ShareStrategy strategy = new DefaultShareStrategy();

    @Test
    void fieldInjectionShouldReuseFirstCandidate() {
        ManagedFixture<String> first = managedFixture("first");
        ManagedFixture<String> second = managedFixture("second");

        Optional<ManagedFixture<String>> selected = strategy.selectCachedFixture(
                fieldScope(),
                request(),
                List.of(first, second)
        );

        Assertions.assertTrue(selected.isPresent());
        Assertions.assertSame(first, selected.get());
    }

    @Test
    void fieldInjectionShouldCacheCreatedFixture() {
        Assertions.assertTrue(strategy.shouldCacheCreatedFixture(fieldScope()));
    }

    @Test
    void parameterInjectionShouldNotSelectCachedFixture() {
        ManagedFixture<String> candidate = managedFixture("candidate");

        Optional<ManagedFixture<String>> selected = strategy.selectCachedFixture(
                parameterScope(),
                request(),
                List.of(candidate)
        );

        Assertions.assertTrue(selected.isEmpty());
    }

    @Test
    void parameterInjectionShouldNotCacheCreatedFixture() {
        Assertions.assertFalse(strategy.shouldCacheCreatedFixture(parameterScope()));
    }

    @Test
    void emptyCandidatesShouldReturnEmpty() {
        Optional<ManagedFixture<String>> selected = strategy.selectCachedFixture(
                fieldScope(),
                request(),
                List.of()
        );

        Assertions.assertTrue(selected.isEmpty());
    }

    private FixtureScopeContext fieldScope() {
        return new FixtureScopeContext(
                "class-scope",
                FixtureScopeContext.InjectionPoint.FIELD,
                "resource",
                null,
                Map.of()
        );
    }

    private FixtureScopeContext parameterScope() {
        return new FixtureScopeContext(
                "method-scope",
                FixtureScopeContext.InjectionPoint.PARAMETER,
                "testMethod",
                0,
                Map.of()
        );
    }

    private FixtureRequest<String> request() {
        return FixtureRequest.of(String.class, CountingProvider.class, DefaultShareStrategy.class);
    }

    private ManagedFixture<String> managedFixture(String value) {
        return new ManagedFixture<>(value, new CountingProvider(), fieldScope());
    }
}
