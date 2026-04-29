package io.github.ajmang.testng.fixture;

import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.FixtureManager;
import io.github.ajmang.tdl.core.fixture.FixtureRequest;
import io.github.ajmang.tdl.core.fixture.FixtureScopeContext;
import io.github.ajmang.tdl.core.fixture.FixtureStore;
import io.github.ajmang.tdl.core.fixture.ManagedFixture;
import io.github.ajmang.tdl.core.fixture.ShareStrategy;
import org.testng.ISuite;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                Thread.currentThread().getId()
        );

        return fixtureManager.getOrCreate(request, scope, new TestngFixtureStore(suite));
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

    private String buildInvocationId(ITestResult testResult) {
        ITestNGMethod method = testResult.getMethod();
        String methodName = method != null ? method.getQualifiedName() : "unknown";
        int instanceIdentity = testResult.getInstance() == null ? 0 : System.identityHashCode(testResult.getInstance());
        return methodName + "::" + instanceIdentity + "::" + testResult.getStartMillis();
    }

    private Class<? extends ShareStrategy> resolveEffectiveStrategy(Fixture annotation, ISuite suite) {
        return resolveConfiguredStrategy(suite).orElse(annotation.strategy());
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

    private static final class TestngFixtureStore implements FixtureStore {

        private final ISuite suite;

        private TestngFixtureStore(ISuite suite) {
            this.suite = suite;
        }

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

