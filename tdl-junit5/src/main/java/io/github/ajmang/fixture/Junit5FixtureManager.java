package io.github.ajmang.fixture;

import io.github.ajmang.tdl.core.fixture.api.Fixture;
import io.github.ajmang.tdl.core.fixture.api.FixtureRequest;
import io.github.ajmang.tdl.core.fixture.strategy.FixtureTags;
import io.github.ajmang.tdl.core.fixture.api.UseFixtureCollectors;
import io.github.ajmang.tdl.core.fixture.context.FixtureContextCollector;
import io.github.ajmang.tdl.core.fixture.context.FixtureContextCollectorInput;
import io.github.ajmang.tdl.core.fixture.context.FixtureContextCollectorRegistry;
import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.runtime.FixtureManager;
import io.github.ajmang.tdl.core.fixture.runtime.FixtureStore;
import io.github.ajmang.tdl.core.fixture.runtime.ManagedFixture;
import io.github.ajmang.tdl.core.fixture.strategy.DefaultShareStrategy;
import io.github.ajmang.tdl.core.fixture.strategy.ShareStrategy;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
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
    private final FixtureContextCollectorRegistry collectorRegistry;

    public Junit5FixtureManager() {
        this(null);
    }

    Junit5FixtureManager(FixtureContextCollectorRegistry collectorRegistry) {
        this.collectorRegistry = collectorRegistry;
    }

    public Object getOrCreate(Class<?> type, Fixture annotation, ExtensionContext context, InjectionMetadata metadata) {
        ExtensionContext scopeContext = resolveStoreContext(context, metadata.injectionPoint());
        ExtensionContext.Store junitStore = scopeContext.getStore(NAMESPACE);
        ExtensionContext methodContext = findMethodContext(context);

        FixtureRequest<?> request = FixtureRequest.of(
                type,
                annotation.provider(),
                resolveEffectiveStrategy(annotation, context)
        );
        String engineRunId = scopeContext.getUniqueId();
        String testClassName = context.getRequiredTestClass().getName();
        String testMethodName = methodContext.getTestMethod().map(Method::getName).orElse(null);
        Set<String> fixtureTags = resolveFixtureTags(context);
        String scopeId = methodContext.getUniqueId();
        FixtureScopeContext scope = new FixtureScopeContext(
                scopeId,
                metadata.injectionPoint(),
                metadata.injectionTarget(),
                metadata.parameterIndex(),
                resolveAttributes(
                        context,
                        methodContext,
                        metadata,
                        engineRunId,
                        testClassName,
                        testMethodName,
                        Thread.currentThread().getId(),
                        fixtureTags,
                        resolveAnnotationNames(context),
                        resolvePackageName(context.getRequiredTestClass())
                )
        );
        return fixtureManager.getOrCreate(request, scope, new JunitFixtureStore(junitStore));
    }

    private Map<String, Object> resolveAttributes(
            ExtensionContext context,
            ExtensionContext methodContext,
            InjectionMetadata metadata,
            String engineRunId,
            String testClassName,
            String testMethodName,
            long threadId,
            Set<String> tags,
            Set<String> annotations,
            String packageName
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FixtureScopeContext.ATTR_ENGINE_RUN_ID, engineRunId);
        attributes.put(FixtureScopeContext.ATTR_TEST_CLASS_NAME, testClassName);
        if (testMethodName != null) {
            attributes.put(FixtureScopeContext.ATTR_TEST_METHOD_NAME, testMethodName);
        }
        attributes.put(FixtureScopeContext.ATTR_THREAD_ID, threadId);
        attributes.put(FixtureScopeContext.ATTR_TAGS, new LinkedHashSet<>(tags));
        attributes.put(FixtureScopeContext.ATTR_ANNOTATIONS, new LinkedHashSet<>(annotations));
        if (packageName != null) {
            attributes.put(FixtureScopeContext.ATTR_PACKAGE_NAME, packageName);
        }
        attributes.put("framework", "junit5");
        attributes.put("junit.tags", new LinkedHashSet<>(tags));
        attributes.put("junit.annotations", new LinkedHashSet<>(annotations));
        if (packageName != null) {
            attributes.put("junit.packageName", packageName);
        }
        Class<?> testClass = context.getRequiredTestClass();
        mergeAttributes(attributes, resolveCollectorRegistry(testClass, testClass.getClassLoader()).collect(
                new FixtureContextCollectorInput(
                        "junit5",
                        context,
                        testClass,
                        methodContext.getTestMethod().orElse(null),
                        metadata.injectionPoint(),
                        metadata.injectionTarget(),
                        metadata.parameterIndex()
                )
        ));
        return attributes;
    }

    private FixtureContextCollectorRegistry resolveCollectorRegistry(Class<?> testClass, ClassLoader classLoader) {
        if (collectorRegistry != null) {
            return collectorRegistry;
        }
        FixtureContextCollectorRegistry fromAnnotation = resolveCollectorRegistryFromAnnotation(testClass);
        if (fromAnnotation != null) {
            return fromAnnotation;
        }
        return FixtureContextCollectorRegistry.load(classLoader);
    }

    private FixtureContextCollectorRegistry resolveCollectorRegistryFromAnnotation(Class<?> testClass) {
        UseFixtureCollectors useFixtureCollectors = testClass.getAnnotation(UseFixtureCollectors.class);
        if (useFixtureCollectors == null || useFixtureCollectors.value().length == 0) {
            return null;
        }

        List<FixtureContextCollector> collectors = new ArrayList<>(useFixtureCollectors.value().length);
        for (Class<? extends FixtureContextCollector> collectorType : useFixtureCollectors.value()) {
            collectors.add(instantiateCollector(collectorType));
        }
        return FixtureContextCollectorRegistry.of(collectors);
    }

    private FixtureContextCollector instantiateCollector(Class<? extends FixtureContextCollector> collectorType) {
        try {
            return collectorType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate collector: " + collectorType.getName(), e);
        }
    }

    private void mergeAttributes(Map<String, Object> target, Map<String, Object> contributed) {
        for (Map.Entry<String, Object> entry : contributed.entrySet()) {
            if (target.containsKey(entry.getKey())) {
                throw new IllegalStateException("Duplicate fixture context attribute key '" + entry.getKey() + "'");
            }
            target.put(entry.getKey(), entry.getValue());
        }
    }

    private Set<String> resolveAnnotationNames(ExtensionContext context) {
        Set<String> names = new LinkedHashSet<>();
        context.getTestClass().ifPresent(testClass -> {
            for (Annotation annotation : testClass.getAnnotations()) {
                names.add(annotation.annotationType().getName());
            }
        });
        context.getTestMethod().ifPresent(testMethod -> {
            for (Annotation annotation : testMethod.getAnnotations()) {
                names.add(annotation.annotationType().getName());
            }
        });
        return names;
    }

    private Set<String> resolveFixtureTags(ExtensionContext context) {
        Set<String> tags = new LinkedHashSet<>();
        context.getTestClass().ifPresent(testClass -> collectFixtureTags(testClass.getAnnotation(FixtureTags.class), tags));
        context.getTestMethod().ifPresent(testMethod -> collectFixtureTags(testMethod.getAnnotation(FixtureTags.class), tags));
        return tags;
    }

    private void collectFixtureTags(FixtureTags fixtureTags, Set<String> target) {
        if (fixtureTags == null || fixtureTags.value().length == 0) {
            return;
        }
        for (String rawTag : fixtureTags.value()) {
            if (rawTag == null) {
                continue;
            }
            String normalized = rawTag.trim();
            if (!normalized.isEmpty()) {
                target.add(normalized);
            }
        }
    }

    private String resolvePackageName(Class<?> testClass) {
        Package targetPackage = testClass.getPackage();
        return targetPackage == null ? null : targetPackage.getName();
    }

    private ExtensionContext resolveStoreContext(ExtensionContext context, FixtureScopeContext.InjectionPoint injectionPoint) {
        if (injectionPoint == FixtureScopeContext.InjectionPoint.FIELD) {
            return findClassContext(context);
        }
        return findMethodContext(context);
    }

    private Class<? extends ShareStrategy> resolveEffectiveStrategy(Fixture annotation, ExtensionContext context) {
        Class<? extends ShareStrategy> annotationStrategy = annotation.strategy();
        if (!DefaultShareStrategy.class.equals(annotationStrategy)) {
            return annotationStrategy;
        }
        return resolveConfiguredStrategy(context).orElse(annotationStrategy);
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
                        "Configured class for key '" + key + "' does not implement ShareStrategy: " + value);
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

    private ExtensionContext findClassContext(ExtensionContext context) {
        ExtensionContext current = context;
        ExtensionContext fallback = context;
        while (current != null) {
            if (current.getTestClass().isPresent()) {
                fallback = current;
                if (current.getTestMethod().isEmpty()) {
                    return current;
                }
            }
            current = current.getParent().orElse(null);
        }
        return fallback;
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

