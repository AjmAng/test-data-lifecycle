package io.github.ajmang.tdl.core.fixture;

import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;
import io.github.ajmang.tdl.core.fixture.support.CountingProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class ManagedFixtureTest {

    @Test
    void closeInvokesDestroy() {
        AtomicBoolean destroyed = new AtomicBoolean(false);
        FixtureProvider<String> provider = new FixtureProvider<>() {
            @Override
            public String create() {
                return "value";
            }

            @Override
            public void destroy(String instance) {
                destroyed.set(true);
            }
        };

        ManagedFixture<String> managed = new ManagedFixture<>("value", provider, scope());
        managed.close();

        Assertions.assertTrue(destroyed.get());
    }

    @Test
    void exposesProducerContext() {
        FixtureScopeContext context = scope();
        ManagedFixture<String> managed = new ManagedFixture<>("value", new CountingProvider(), context);

        Assertions.assertSame(context, managed.producerContext());
    }

    @Test
    void exposesFixtureAndProvider() {
        CountingProvider provider = new CountingProvider();
        ManagedFixture<String> managed = new ManagedFixture<>("value", provider, scope());

        Assertions.assertEquals("value", managed.fixture());
        Assertions.assertSame(provider, managed.provider());
    }

    @Test
    void cleanupPolicyForwardsToProvider() {
        ManagedFixture<String> always = new ManagedFixture<>("value", new CountingProvider(CleanupPolicy.ALWAYS), scope());
        ManagedFixture<String> never = new ManagedFixture<>("value", new CountingProvider(CleanupPolicy.NEVER), scope());
        ManagedFixture<String> onSuccess = new ManagedFixture<>("value", new CountingProvider(CleanupPolicy.ON_SUCCESS), scope());

        Assertions.assertEquals(CleanupPolicy.ALWAYS, always.cleanupPolicy());
        Assertions.assertEquals(CleanupPolicy.NEVER, never.cleanupPolicy());
        Assertions.assertEquals(CleanupPolicy.ON_SUCCESS, onSuccess.cleanupPolicy());
    }

    @Test
    void shouldDestroyRespectsPolicy() {
        ManagedFixture<String> always = new ManagedFixture<>("value", new CountingProvider(CleanupPolicy.ALWAYS), scope());
        ManagedFixture<String> never = new ManagedFixture<>("value", new CountingProvider(CleanupPolicy.NEVER), scope());
        ManagedFixture<String> onSuccess = new ManagedFixture<>("value", new CountingProvider(CleanupPolicy.ON_SUCCESS), scope());

        Assertions.assertTrue(always.shouldDestroy(true));
        Assertions.assertTrue(always.shouldDestroy(false));

        Assertions.assertFalse(never.shouldDestroy(true));
        Assertions.assertFalse(never.shouldDestroy(false));

        Assertions.assertTrue(onSuccess.shouldDestroy(true));
        Assertions.assertFalse(onSuccess.shouldDestroy(false));
    }

    private FixtureScopeContext scope() {
        return new FixtureScopeContext(
                "scope",
                FixtureScopeContext.InjectionPoint.FIELD,
                "field",
                null,
                Map.of()
        );
    }
}
