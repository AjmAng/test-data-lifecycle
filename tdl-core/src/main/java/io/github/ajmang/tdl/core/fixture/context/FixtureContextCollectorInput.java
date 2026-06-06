package io.github.ajmang.tdl.core.fixture.context;

import java.lang.reflect.Method;
import java.util.Optional;

public record FixtureContextCollectorInput(
        String framework,
        Object frameworkContext,
        Class<?> testClass,
        Method testMethod,
        FixtureScopeContext.InjectionPoint injectionPoint,
        String injectionTarget,
        Integer parameterIndex
) {

    public <T> Optional<T> frameworkContext(Class<T> type) {
        if (type.isInstance(frameworkContext)) {
            return Optional.of(type.cast(frameworkContext));
        }
        return Optional.empty();
    }
}


