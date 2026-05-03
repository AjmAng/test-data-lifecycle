package io.github.ajmang.fixture;

import io.github.ajmang.tdl.core.fixture.EagerFetch;
import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.FixtureScopeContext;
import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Field;
import java.util.Optional;

public class FixtureExtension implements
        ParameterResolver,
        BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback
{

    private final Junit5FixtureManager fixtureManager = new Junit5FixtureManager();

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {

    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {

    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
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
            fixtureManager.getOrCreate(field.getType(), fixture, extensionContext, metadata);
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
                null
        );
        return annotation.map(fixture -> fixtureManager.getOrCreate(parameterContext.getParameter().getType(), fixture, extensionContext, metadata)).orElse(null);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        Object testInstance = extensionContext.getRequiredTestInstance();
        for (Field field : testInstance.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Fixture.class)) {
                InjectionMetadata metadata = new InjectionMetadata(
                        FixtureScopeContext.InjectionPoint.FIELD, field.getName(),  null
                );

                field.setAccessible(true);
                Object fixture = fixtureManager.getOrCreate(field.getType(), field.getAnnotation(Fixture.class), extensionContext, metadata);
                field.set(testInstance, fixture);
            }
        }
    }
}
