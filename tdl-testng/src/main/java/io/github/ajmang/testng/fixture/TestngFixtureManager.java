package io.github.ajmang.testng.fixture;

import io.github.ajmang.tdl.core.fixture.*;
import org.testng.ISuite;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class TestngFixtureManager {

    private static final String[] DEFAULT_STRATEGY_KEYS = new String[]{
            "tdl.fixture.default-strategy-class",
            "tdl.testng.fixture.default-strategy-class"
    };

    private static final String FIXTURE_MAP_KEY = TestngFixtureManager.class.getName() + ".fixtures";

    private final FixtureManager fixtureManager = new FixtureManager();
    private final FixtureContextCollectorRegistry collectorRegistry;

    public TestngFixtureManager() {
        this(null);
    }

    TestngFixtureManager(FixtureContextCollectorRegistry collectorRegistry) {
        this.collectorRegistry = collectorRegistry;
    }

    public static void closeAll(ISuite suite) {
        Object stored = suite.getAttribute(FIXTURE_MAP_KEY);
        if (!(stored instanceof ConcurrentMap<?, ?> rawMap)) {
            return;
        }

        @SuppressWarnings("unchecked")
        ConcurrentMap<String, ManagedFixture<?>> fixtureMap = (ConcurrentMap<String, ManagedFixture<?>>) rawMap;
        for (ManagedFixture<?> fixture : fixtureMap.values()) {
            fixture.close();
        }
        fixtureMap.clear();
        suite.removeAttribute(FIXTURE_MAP_KEY);
    }

    public Object getOrCreate(Class<?> type, Fixture annotation, ITestResult testResult, InjectionMetadata metadata) {
        ISuite suite = testResult.getTestContext().getSuite();
        ITestNGMethod method = testResult.getMethod();

        FixtureRequest<?> request = FixtureRequest.of(
                type,
                annotation.provider(),
                resolveEffectiveStrategy(annotation, suite)
        );

        FixtureScopeContext scope = new FixtureScopeContext(
                suite.getName(),
                testResult.getTestClass().getRealClass().getName(),
                method != null ? method.getMethodName() : null,
                buildInvocationId(testResult),
                metadata.injectionPoint(),
                metadata.injectionTarget(),
                metadata.parameterIndex(),
                Thread.currentThread().getId(),
                resolveTags(method),
                resolveAnnotationNames(testResult),
                resolvePackageName(testResult),
                resolveAttributes(method, testResult, metadata)
        );

        return fixtureManager.getOrCreate(request, scope, new TestngFixtureStore(suite));
    }

    private Map<String, Object> resolveAttributes(ITestNGMethod method, ITestResult testResult, InjectionMetadata metadata) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("framework", "testng");
        attributes.put("testng.groups", resolveTags(method));
        attributes.put("testng.annotations", resolveAnnotationNames(testResult));
        attributes.put("testng.packageName", resolvePackageName(testResult));
        Class<?> testClass = testResult.getTestClass().getRealClass();
        mergeAttributes(attributes, resolveCollectorRegistry(testClass, testClass.getClassLoader()).collect(
                new FixtureContextCollectorInput(
                        "testng",
                        testResult,
                        testClass,
                        resolveJavaMethod(method),
                        metadata.injectionPoint(),
                        metadata.injectionTarget(),
                        metadata.parameterIndex()
                )
        ));
        return attributes;
    }

    private Method resolveJavaMethod(ITestNGMethod method) {
        if (method == null) {
            return null;
        }
        return method.getConstructorOrMethod().getMethod();
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

    private String buildInvocationId(ITestResult testResult) {
        ITestNGMethod method = testResult.getMethod();
        String methodName = method != null ? method.getQualifiedName() : "unknown";
        int instanceIdentity = testResult.getInstance() == null ? 0 : System.identityHashCode(testResult.getInstance());
        return methodName + "::" + instanceIdentity + "::" + testResult.getStartMillis();
    }

    private Class<? extends ShareStrategy> resolveEffectiveStrategy(Fixture annotation, ISuite suite) {
        Class<? extends ShareStrategy> annotationStrategy = annotation.strategy();
        if (!DefaultShareStrategy.class.equals(annotationStrategy)) {
            return annotationStrategy;
        }
        return resolveConfiguredStrategy(suite).orElse(annotationStrategy);
    }

    private Set<String> resolveTags(ITestNGMethod method) {
        if (method == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(method.getGroups()));
    }

    private Set<String> resolveAnnotationNames(ITestResult testResult) {
        Set<String> names = new LinkedHashSet<>();
        Class<?> testClass = testResult.getTestClass().getRealClass();
        for (Annotation annotation : testClass.getAnnotations()) {
            names.add(annotation.annotationType().getName());
        }

        ITestNGMethod testMethod = testResult.getMethod();
        if (testMethod != null) {
            Method method = resolveJavaMethod(testMethod);
            if (method != null) {
                for (Annotation annotation : method.getAnnotations()) {
                    names.add(annotation.annotationType().getName());
                }
            }
        }
        return names;
    }

    private String resolvePackageName(ITestResult testResult) {
        Class<?> testClass = testResult.getTestClass().getRealClass();
        Package targetPackage = testClass.getPackage();
        return targetPackage == null ? null : targetPackage.getName();
    }

    private Optional<Class<? extends ShareStrategy>> resolveConfiguredStrategy(ISuite suite) {
        for (String key : DEFAULT_STRATEGY_KEYS) {
            String suiteValue = suite.getParameter(key);
            if (suiteValue != null && !suiteValue.isBlank()) {
                return Optional.of(parseStrategyClass(suiteValue, key));
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

    private record TestngFixtureStore(ISuite suite) implements FixtureStore {

        @Override
        public ManagedFixture<?> getOrComputeIfAbsent(String key, Supplier<ManagedFixture<?>> supplier) {
            return fixtureMap().computeIfAbsent(key, ignored -> supplier.get());
        }

        @Override
        public List<ManagedFixture<?>> listAll() {
            return new ArrayList<>(fixtureMap().values());
        }

        @Override
        public void put(String key, ManagedFixture<?> fixture) {
            fixtureMap().put(key, fixture);
        }

        @SuppressWarnings("unchecked")
        private ConcurrentMap<String, ManagedFixture<?>> fixtureMap() {
            Object existing = suite.getAttribute(FIXTURE_MAP_KEY);
            if (existing instanceof ConcurrentMap<?, ?> rawMap) {
                return (ConcurrentMap<String, ManagedFixture<?>>) rawMap;
            }

            synchronized (suite) {
                Object current = suite.getAttribute(FIXTURE_MAP_KEY);
                if (current instanceof ConcurrentMap<?, ?> rawMap) {
                    return (ConcurrentMap<String, ManagedFixture<?>>) rawMap;
                }
                ConcurrentMap<String, ManagedFixture<?>> created = new ConcurrentHashMap<>();
                suite.setAttribute(FIXTURE_MAP_KEY, created);
                return created;
            }
        }
    }
}

