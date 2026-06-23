package io.github.ajmang.tdl.core.fixture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class RetryPolicyTest {

    @Test
    void nonePolicyHasSingleAttemptAndZeroBackoff() {
        RetryPolicy policy = RetryPolicy.none();

        Assertions.assertEquals(1, policy.maxAttempts());
        Assertions.assertEquals(Duration.ZERO, policy.backoff());
        Assertions.assertTrue(policy.isRetryable(new Exception()));
        Assertions.assertTrue(policy.isRetryable(new RuntimeException()));
    }

    @Test
    void fixedPolicyUsesProvidedValues() {
        RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(100), IllegalStateException.class);

        Assertions.assertEquals(3, policy.maxAttempts());
        Assertions.assertEquals(Duration.ofMillis(100), policy.backoff());
        Assertions.assertTrue(policy.isRetryable(new IllegalStateException()));
        Assertions.assertFalse(policy.isRetryable(new IllegalArgumentException()));
    }

    @Test
    void fixedPolicyDefaultsToRetryOnException() {
        RetryPolicy policy = RetryPolicy.fixed(2, Duration.ZERO);

        Assertions.assertTrue(policy.isRetryable(new Exception()));
        Assertions.assertTrue(policy.isRetryable(new RuntimeException()));
        Assertions.assertTrue(policy.isRetryable(new IllegalStateException()));
    }

    @Test
    void rejectsNullRetryOn() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new RetryPolicy(1, Duration.ZERO, null));
    }

    @Test
    void isRetryableMatchesSubclasses() {
        RetryPolicy policy = RetryPolicy.fixed(2, Duration.ZERO, RuntimeException.class);

        Assertions.assertTrue(policy.isRetryable(new IllegalStateException()));
        Assertions.assertTrue(policy.isRetryable(new NullPointerException()));
        Assertions.assertFalse(policy.isRetryable(new Exception()));
    }
}
