package io.github.ajmang.fixture.tdl;

import io.github.ajmang.tdl.core.fixture.EagerFetch;
import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.context.FixtureScopeContext;
import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FixtureExtension implements
        ParameterResolver,
        BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback,
        TestExecutionExceptionHandler
{

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(FixtureExtension.class);
    private static final String CLASS_PREFETCH_CACHE_KEY = "tdl.junit5.class.prefetch.cache";
    private static final String TEST_FAILED_KEY = "tdl.junit5.test.failed";

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
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        ExtensionContext methodContext = findMethodContext(context);
        methodContext.getStore(FixtureExtension.NAMESPACE).put(TEST_FAILED_KEY, true);
        // Also mark class-level failure so class-scoped fixtures can react in afterAll
        findClassContext(context).getStore(FixtureExtension.NAMESPACE).put(TEST_FAILED_KEY, true);
        throw throwable;
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        ExtensionContext classContext = findClassContext(extensionContext);
        boolean classPassed = !Boolean.TRUE.equals(classContext.getStore(NAMESPACE).get(TEST_FAILED_KEY));
        fixtureManager.cleanupClassFixtures(classContext, classPassed);
        classPrefetchCache(extensionContext).clear();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        ExtensionContext methodContext = findMethodContext(extensionContext);
        boolean testPassed = !Boolean.TRUE.equals(methodContext.getStore(NAMESPACE).get(TEST_FAILED_KEY));
        fixtureManager.cleanupAfterTest(methodContext, testPassed);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
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
        ExtensionContext methodContext = findMethodContext(extensionContext);
        methodContext.getStore(NAMESPACE).put(TEST_FAILED_KEY, false);
        try {
            Object testInstance = extensionContext.getRequiredTestInstance();
            Map<String, Object> prefetchCache = classPrefetchCache(extensionContext);
            for (Field field : testInstance.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Fixture.class)) {
                    Fixture fixtureAnnotation = field.getAnnotation(Fixture.class);
                    InjectionMetadata metadata = new InjectionMetadata(
                            FixtureScopeContext.InjectionPoint.FIELD, field.getName(), null
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
        } catch (Exception e) {
            methodContext.getStore(NAMESPACE).put(TEST_FAILED_KEY, true);
            findClassContext(extensionContext).getStore(NAMESPACE).put(TEST_FAILED_KEY, true);
            throw e;
        }
    }
}
