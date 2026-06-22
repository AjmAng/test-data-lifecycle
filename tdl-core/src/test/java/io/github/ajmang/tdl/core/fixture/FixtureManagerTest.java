package io.github.ajmang.tdl.core.fixture;

import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.FixtureManager;
import io.github.ajmang.tdl.core.fixture.share.DefaultShareStrategy;
import io.github.ajmang.tdl.core.fixture.share.SharedByTagStrategy;
import io.github.ajmang.tdl.core.fixture.support.CountingProvider;
import io.github.ajmang.tdl.core.fixture.support.InMemoryFixtureStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

class FixtureManagerTest {

    private final FixtureManager fixtureManager = new FixtureManager();
    private final InMemoryFixtureStore store = new InMemoryFixtureStore();

    @BeforeEach
    void reset() {
        CountingProvider.reset();
    }

    @Test
    void createsFixtureWhenStoreIsEmpty() {
        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                DefaultShareStrategy.class
        );

        String fixture = fixtureManager.getOrCreate(request, fieldScope(), store);

        Assertions.assertNotNull(fixture);
        Assertions.assertEquals("fixture-1", fixture);
        Assertions.assertEquals(1, CountingProvider.CREATIONS.get());
    }

    @Test
    void reusesCachedFixtureForFieldInjection() {
        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                DefaultShareStrategy.class
        );

        String first = fixtureManager.getOrCreate(request, fieldScope("scope-a"), store);
        String second = fixtureManager.getOrCreate(request, fieldScope("scope-b"), store);

        Assertions.assertSame(first, second);
        Assertions.assertEquals(1, CountingProvider.CREATIONS.get());
    }

    @Test
    void isolatesParameterInjectionEvenWithSameRequest() {
        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                DefaultShareStrategy.class
        );

        String first = fixtureManager.getOrCreate(request, parameterScope("scope-a"), store);
        String second = fixtureManager.getOrCreate(request, parameterScope("scope-b"), store);

        Assertions.assertNotSame(first, second);
        Assertions.assertEquals(2, CountingProvider.CREATIONS.get());
    }

    @Test
    void differentProviderTypesCreateDifferentFixtures() {
        FixtureRequest<String> requestA = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                DefaultShareStrategy.class
        );
        FixtureRequest<String> requestB = FixtureRequest.of(
                String.class,
                AnotherCountingProvider.class,
                DefaultShareStrategy.class
        );

        // Use separate stores to avoid cross-provider sharing via candidates.
        InMemoryFixtureStore storeA = new InMemoryFixtureStore();
        InMemoryFixtureStore storeB = new InMemoryFixtureStore();

        String fixtureA = fixtureManager.getOrCreate(requestA, fieldScope(), storeA);
        String fixtureB = fixtureManager.getOrCreate(requestB, fieldScope(), storeB);

        Assertions.assertNotEquals(fixtureA, fixtureB);
    }

    @Test
    void propagatesProviderCreationException() {
        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                ExplodingProvider.class,
                DefaultShareStrategy.class
        );

        RuntimeException exception = Assertions.assertThrows(
                RuntimeException.class,
                () -> fixtureManager.getOrCreate(request, fieldScope(), store)
        );

        Assertions.assertTrue(exception.getMessage().contains("Failed to create fixture"));
        Assertions.assertNotNull(exception.getCause());
        Assertions.assertTrue(
                containsInCauseChain(exception, ExplodingProvider.ERROR),
                "Expected original error in cause chain"
        );
    }

    @Test
    void propagatesStrategyInstantiationFailure() {
        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                NonInstantiableStrategy.class
        );

        RuntimeException exception = Assertions.assertThrows(
                RuntimeException.class,
                () -> fixtureManager.getOrCreate(request, fieldScope(), store)
        );

        Assertions.assertTrue(exception.getMessage().contains("Failed to create fixture"));
        Assertions.assertNotNull(exception.getCause());
        Assertions.assertTrue(exception.getCause().getMessage().contains("Failed to create strategy"));
    }

    @Test
    void tagBasedStrategySharesAcrossScopes() {
        FixtureRequest<String> request = FixtureRequest.of(
                String.class,
                CountingProvider.class,
                SharedByTagStrategy.class
        );

        String first = fixtureManager.getOrCreate(request, taggedFieldScope("scope-a", Set.of("integration")), store);
        String second = fixtureManager.getOrCreate(request, taggedFieldScope("scope-b", Set.of("integration")), store);

        Assertions.assertSame(first, second);
        Assertions.assertEquals(1, CountingProvider.CREATIONS.get());
    }

    private FixtureScopeContext fieldScope() {
        return fieldScope("scope-default");
    }

    private FixtureScopeContext fieldScope(String scopeId) {
        return new FixtureScopeContext(
                scopeId,
                FixtureScopeContext.InjectionPoint.FIELD,
                "resource",
                null,
                Map.of()
        );
    }

    private FixtureScopeContext parameterScope(String scopeId) {
        return new FixtureScopeContext(
                scopeId,
                FixtureScopeContext.InjectionPoint.PARAMETER,
                "testMethod",
                0,
                Map.of()
        );
    }

    private FixtureScopeContext taggedFieldScope(String scopeId, Set<String> tags) {
        return new FixtureScopeContext(
                scopeId,
                FixtureScopeContext.InjectionPoint.FIELD,
                "resource",
                null,
                Map.of(FixtureScopeContext.ATTR_TAGS, tags)
        );
    }

    public static class AnotherCountingProvider extends CountingProvider {
        // Explicit no-arg constructor required because FixtureManager instantiates
        // providers via reflection using their no-arg constructor.
        public AnotherCountingProvider() {
            super("another");
        }
    }

    private static boolean containsInCauseChain(Throwable outer, Throwable target) {
        Throwable current = outer;
        while (current != null) {
            if (current == target) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static class ExplodingProvider implements FixtureProvider<String> {
        static final IllegalStateException ERROR = new IllegalStateException("boom");

        @Override
        public String create() {
            throw ERROR;
        }

        @Override
        public void destroy(String instance) {
        }
    }
}
