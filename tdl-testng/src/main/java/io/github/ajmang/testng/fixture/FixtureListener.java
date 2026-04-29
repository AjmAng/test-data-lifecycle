package io.github.ajmang.testng.fixture;

import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.FixtureScopeContext;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestResult;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class FixtureListener implements IInvokedMethodListener, ISuiteListener {

    private final TestngFixtureManager fixtureManager = new TestngFixtureManager();

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (testResult == null || testResult.getInstance() == null) {
            return;
        }
        injectFixtures(testResult.getInstance(), testResult);
    }

    @Override
    public void onFinish(ISuite suite) {
        TestngFixtureManager.closeAll(suite);
    }

    private void injectFixtures(Object testInstance, ITestResult testResult) {
        for (Class<?> current = testInstance.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                Fixture annotation = field.getAnnotation(Fixture.class);
                if (annotation == null) {
                    continue;
                }

                InjectionMetadata metadata = new InjectionMetadata(
                        FixtureScopeContext.InjectionPoint.FIELD,
                        field.getName(),
                        null
                );

                field.setAccessible(true);
                Object fixture = fixtureManager.getOrCreate(field.getType(), annotation, testResult, metadata);
                Object target = Modifier.isStatic(field.getModifiers()) ? null : testInstance;
                try {
                    field.set(target, fixture);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to inject fixture into field: " + field.getName(), e);
                }
            }
        }
    }
}

