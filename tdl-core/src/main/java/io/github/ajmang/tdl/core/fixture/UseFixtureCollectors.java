package io.github.ajmang.tdl.core.fixture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares explicit collectors for a test class.
 * When present, adapters use these collectors instead of ServiceLoader discovery.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface UseFixtureCollectors {

    Class<? extends FixtureContextCollector>[] value();
}

