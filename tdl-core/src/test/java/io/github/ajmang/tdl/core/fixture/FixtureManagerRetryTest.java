package io.github.ajmang.tdl.core.fixture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

class FixtureManagerRetryTest {

    private final FixtureManager fixtureManager = new FixtureManager();
    private final FixtureStore fixtureStore = new InMemoryFixtureStore();

    @BeforeEach
    void resetAttempts() {
        FlakyThenSuccessProvider.attempts.set(0);
        AlwaysFailRetryableProvider.attempts.set(0);
        NonRetryableFailureProvider.attempts.set(0);
    }

    @Test
    void retriesAndEventuallySucceeds() {
        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                FlakyThenSuccessProvider.class,
                DefaultShareStrategy.class
        );

        String fixture = fixtureManager.getOrCreate(request, parameterScope(), fixtureStore);

        Assertions.assertEquals("ready", fixture);
        Assertions.assertEquals(3, FlakyThenSuccessProvider.attempts.get());
    }

    @Test
    void failsAfterMaxAttemptsForRetryableExceptions() {
        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                AlwaysFailRetryableProvider.class,
                DefaultShareStrategy.class
        );

        RuntimeException exception = Assertions.assertThrows(
                RuntimeException.class,
                () -> fixtureManager.getOrCreate(request, parameterScope(), fixtureStore)
        );

        Assertions.assertTrue(exception.getMessage().contains("Failed to create fixture for type"));
        Assertions.assertEquals(2, AlwaysFailRetryableProvider.attempts.get());
    }

    @Test
    void doesNotRetryForNonRetryableException() {
        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                NonRetryableFailureProvider.class,
                DefaultShareStrategy.class
        );

        Assertions.assertThrows(
                RuntimeException.class,
                () -> fixtureManager.getOrCreate(request, parameterScope(), fixtureStore)
        );

        Assertions.assertEquals(1, NonRetryableFailureProvider.attempts.get());
    }

    private FixtureScopeContext parameterScope() {
        return new FixtureScopeContext(
                "run-1",
                "io.github.ajmang.tdl.tests.RetryTest",
                "createsFixture",
                "uid-1",
                FixtureScopeContext.InjectionPoint.PARAMETER,
                "arg0",
                0,
                Thread.currentThread().getId()
        );
    }

    static class FlakyThenSuccessProvider implements FixtureProvider<String> {
        static final AtomicInteger attempts = new AtomicInteger();

        @Override
        public String create() {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new IllegalStateException("transient");
            }
            return "ready";
        }

        @Override
        public void destroy(String instance) {
        }

        @Override
        public RetryPolicy retryPolicy() {
            return RetryPolicy.fixed(3, Duration.ZERO, IllegalStateException.class);
        }
    }

    static class AlwaysFailRetryableProvider implements FixtureProvider<String> {
        static final AtomicInteger attempts = new AtomicInteger();

        @Override
        public String create() {
            attempts.incrementAndGet();
            throw new IllegalStateException("always fails");
        }

        @Override
        public void destroy(String instance) {
        }

        @Override
        public RetryPolicy retryPolicy() {
            return RetryPolicy.fixed(2, Duration.ZERO, IllegalStateException.class);
        }
    }

    static class NonRetryableFailureProvider implements FixtureProvider<String> {
        static final AtomicInteger attempts = new AtomicInteger();

        @Override
        public String create() {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("non retryable");
        }

        @Override
        public void destroy(String instance) {
        }

        @Override
        public RetryPolicy retryPolicy() {
            return RetryPolicy.fixed(5, Duration.ZERO, IllegalStateException.class);
        }
    }

    static class InMemoryFixtureStore implements FixtureStore {
        private final Map<String, ManagedFixture<?>> fixtures = new LinkedHashMap<>();

        @Override
        public ManagedFixture<?> getOrComputeIfAbsent(String key, Supplier<ManagedFixture<?>> supplier) {
            return fixtures.computeIfAbsent(key, ignored -> supplier.get());
        }

        @Override
        public List<ManagedFixture<?>> listAll() {
            return new ArrayList<>(fixtures.values());
        }

        @Override
        public void put(String key, ManagedFixture<?> fixture) {
            fixtures.put(key, fixture);
        }
    }
}

