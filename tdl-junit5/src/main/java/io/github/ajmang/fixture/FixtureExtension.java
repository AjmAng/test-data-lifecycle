package io.github.ajmang.fixture;

import io.github.ajmang.tdl.core.fixture.EagerFetch;
import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.FixtureScopeContext;
import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FixtureExtension implements
        ParameterResolver,
        BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback
{

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(FixtureExtension.class);
    private static final String CLASS_PREFETCH_CACHE_KEY = "tdl.junit5.class.prefetch.cache";

    private final Junit5FixtureManager fixtureManager = new Junit5FixtureManager();

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> classPrefetchCache(ExtensionContext context) {
        ExtensionContext classContext = findClassContext(context);
        return classContext.getStore(NAMESPACE).getOrComputeIfAbsent(
                CLASS_PREFETCH_CACHE_KEY,
                key -> new ConcurrentHashMap<String, Object>(),
                Map.class
        );
    }

    private String prefetchKey(Field field) {
        return field.getDeclaringClass().getName() + "#" + field.getName();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        classPrefetchCache(extensionContext).clear();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {

    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        Map<String, Object> prefetchCache = classPrefetchCache(extensionContext);
        for (Field field : extensionContext.getRequiredTestClass().getDeclaredFields()) {
            Fixture fixture = field.getAnnotation(Fixture.class);
            if (fixture == null || fixture.eagerFetch() != EagerFetch.ENABLED) {
                continue;
            }

            InjectionMetadata metadata = new InjectionMetadata(
                    FixtureScopeContext.InjectionPoint.FIELD,
                    field.getName(),
                    null
            );
            Object prefetched = fixtureManager.getOrCreate(field.getType(), fixture, extensionContext, metadata);
            prefetchCache.put(prefetchKey(field), prefetched);
        }
    }



    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.findAnnotation(io.github.ajmang.tdl.core.fixture.Fixture.class).isPresent();
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        // set parameter value which is annotated with @Fixture
        Optional<Fixture> annotation = parameterContext.findAnnotation(io.github.ajmang.tdl.core.fixture.Fixture.class);
        InjectionMetadata metadata = new InjectionMetadata(
                FixtureScopeContext.InjectionPoint.PARAMETER,
                parameterContext.getDeclaringExecutable().getName(),
                parameterContext.getIndex()
        );
        return annotation.map(fixture -> fixtureManager.getOrCreate(parameterContext.getParameter().getType(), fixture, extensionContext, metadata)).orElse(null);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        Object testInstance = extensionContext.getRequiredTestInstance();
        Map<String, Object> prefetchCache = classPrefetchCache(extensionContext);
        for (Field field : testInstance.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Fixture.class)) {
                Fixture fixtureAnnotation = field.getAnnotation(Fixture.class);
                InjectionMetadata metadata = new InjectionMetadata(
                        FixtureScopeContext.InjectionPoint.FIELD, field.getName(),  null
                );

                field.setAccessible(true);
                Object fixture;
                if (fixtureAnnotation.eagerFetch() == EagerFetch.ENABLED) {
                    fixture = prefetchCache.get(prefetchKey(field));
                    if (fixture == null) {
                        fixture = fixtureManager.getOrCreate(field.getType(), fixtureAnnotation, extensionContext, metadata);
                        prefetchCache.put(prefetchKey(field), fixture);
                    }
                } else {
                    fixture = fixtureManager.getOrCreate(field.getType(), fixtureAnnotation, extensionContext, metadata);
                }
                field.set(testInstance, fixture);
            }
        }
    }
}
