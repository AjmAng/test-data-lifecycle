package io.github.ajmang.tdl.core.fixture;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

public record RetryPolicy(int maxAttempts, Duration backoff, Class<? extends Throwable>[] retryOn) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        Objects.requireNonNull(backoff, "backoff must not be null");
        Objects.requireNonNull(retryOn, "retryOn must not be null");
    }

    @SuppressWarnings("unchecked")
    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, new Class[]{Exception.class});
    }

    @SafeVarargs
    public static RetryPolicy fixed(int maxAttempts, Duration backoff, Class<? extends Throwable>... retryOn) {
        Class<? extends Throwable>[] target = retryOn == null || retryOn.length == 0
                ? defaultRetryOn()
                : retryOn;
        return new RetryPolicy(maxAttempts, backoff, target);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Throwable>[] defaultRetryOn() {
        return new Class[]{Exception.class};
    }

    public boolean isRetryable(Throwable throwable) {
        return Arrays.stream(retryOn).anyMatch(type -> type.isInstance(throwable));
    }
}
