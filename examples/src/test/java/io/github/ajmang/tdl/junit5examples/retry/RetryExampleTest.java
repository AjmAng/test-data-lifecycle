package io.github.ajmang.tdl.junit5examples.retry;

import io.github.ajmang.fixture.tdl.FixtureExtension;
import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.FixtureProvider;
import io.github.ajmang.tdl.core.fixture.RetryPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example demonstrating RetryPolicy with a flaky provider.
 */
@ExtendWith(FixtureExtension.class)
class RetryExampleTest {

    @BeforeEach
    void reset() {
        FlakyDatabaseProvider.attempts.set(0);
    }

    @Test
    void retrySucceedsAfterTransientFailures(
            @Fixture(provider = FlakyDatabaseProvider.class) Database db
    ) {
        Assertions.assertNotNull(db);
        Assertions.assertEquals("connected", db.state());
        Assertions.assertEquals(3, FlakyDatabaseProvider.attempts.get(),
                "Provider should be invoked three times before success");
    }

    public record Database(String state) {
    }

    public static class FlakyDatabaseProvider implements FixtureProvider<Database> {
        static final AtomicInteger attempts = new AtomicInteger();

        @Override
        public Database create() {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new IllegalStateException("Connection refused (transient)");
            }
            return new Database("connected");
        }

        @Override
        public void destroy(Database instance) {
        }

        @Override
        public RetryPolicy retryPolicy() {
            return RetryPolicy.fixed(3, Duration.ZERO, IllegalStateException.class);
        }
    }
}
