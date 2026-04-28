package io.github.ajmang.tdl.core.fixture;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Fixture {


    Class<? extends FixtureProvider<?>> provider();


    Class<? extends ShareStrategy> strategy() default DefaultShareStrategy.class;

}

