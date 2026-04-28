package io.github.ajmang.fixture;

import io.github.ajmang.tdl.core.fixture.*;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class Junit5FixtureManager {

    private static final String[] DEFAULT_STRATEGY_KEYS = new String[]{
            "tdl.fixture.default-strategy-class",
            "tdl.junit5.fixture.default-strategy-class"
    };

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(Junit5FixtureManager.class);

    private final FixtureManager fixtureManager = new FixtureManager();

    public Object getOrCreate(Class<?> type, Fixture annotation, ExtensionContext context, InjectionMetadata metadata) {
        ExtensionContext scopeContext = context.getRoot();
        ExtensionContext.Store junitStore = scopeContext.getStore(NAMESPACE);
        ExtensionContext methodContext = findMethodContext(context);

        FixtureRequest<?> request = FixtureRequest.of(
                type,
                annotation.provider(),
                resolveEffectiveStrategy(annotation, context)
        );
        FixtureScopeContext scope = new FixtureScopeContext(
                scopeContext.getUniqueId(),
                context.getRequiredTestClass().getName(),
                methodContext.getTestMethod().map(Method::getName).orElse(null),
                methodContext.getUniqueId(),
                metadata.injectionPoint(),
                metadata.injectionTarget(),
                metadata.parameterIndex(),
                Thread.currentThread().getId()
        );
        return fixtureManager.getOrCreate(request, scope, new JunitFixtureStore(junitStore));
    }

    private Class<? extends ShareStrategy> resolveEffectiveStrategy(io.github.ajmang.tdl.core.fixture.Fixture annotation, ExtensionContext context) {
        return resolveConfiguredStrategy(context).orElse(annotation.strategy());
    }

    private Optional<Class<? extends ShareStrategy>> resolveConfiguredStrategy(ExtensionContext context) {
        for (String key : DEFAULT_STRATEGY_KEYS) {
            Optional<String> value = context.getConfigurationParameter(key);
            if (value.isPresent()) {
                return Optional.of(parseStrategyClass(value.get(), key));
            }

            String systemValue = System.getProperty(key);
            if (systemValue != null && !systemValue.isBlank()) {
                return Optional.of(parseStrategyClass(systemValue, key));
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Class<? extends ShareStrategy> parseStrategyClass(String value, String key) {
        try {
            Class<?> clazz = Class.forName(value.trim());
            if (!ShareStrategy.class.isAssignableFrom(clazz)) {
                throw new RuntimeException(
                        "Configured class for key '" + key + "' does not implement FixtureIsolationStrategy: " + value);
            }
            return (Class<? extends ShareStrategy>) clazz;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Invalid strategy class for key '" + key + "': " + value, e);
        }
    }

    private ExtensionContext findMethodContext(ExtensionContext context) {
        ExtensionContext current = context;
        while (current != null) {
            if (current.getTestMethod().isPresent()) {
                return current;
            }
            current = current.getParent().orElse(null);
        }
        return context;
    }

    private record JunitFixtureStore(ExtensionContext.Store store) implements FixtureStore {

        private static final String FIXTURE_KEYS_INDEX = "tdl.fixture.keys.index";

        @SuppressWarnings("unchecked")
        private Set<String> keysIndex() {
            return (Set<String>) store.getOrComputeIfAbsent(
                    FIXTURE_KEYS_INDEX,
                    key -> ConcurrentHashMap.<String>newKeySet(),
                    Set.class
            );
        }

        @Override
        public ManagedFixture<?> getOrComputeIfAbsent(String key, Supplier<ManagedFixture<?>> supplier) {
            JunitManagedFixtureResource resource = store.getOrComputeIfAbsent(
                    key,
                    k -> new JunitManagedFixtureResource(supplier.get()),
                    JunitManagedFixtureResource.class
            );
            keysIndex().add(key);
            return resource.managedFixture;
        }

        @Override
        public List<ManagedFixture<?>> listAll() {
            List<ManagedFixture<?>> fixtures = new ArrayList<>();
            for (String key : keysIndex()) {
                JunitManagedFixtureResource resource = store.get(key, JunitManagedFixtureResource.class);
                if (resource != null) {
                    fixtures.add(resource.managedFixture);
                }
            }
            return fixtures;
        }

        @Override
        public void put(String key, ManagedFixture<?> fixture) {
            store.put(key, new JunitManagedFixtureResource(fixture));
            keysIndex().add(key);
        }
    }

    private record JunitManagedFixtureResource(
            ManagedFixture<?> managedFixture) implements ExtensionContext.Store.CloseableResource {

        @Override
        public void close() {
            managedFixture.close();
        }
    }
}

