package io.github.ajmang.tdl.core.fixture;

/**
 * Defines when a fixture should be destroyed.
 * Similar to RetryPolicy, but for cleanup/destroy behavior.
 */
public enum CleanupPolicy {
    /**
     * Always destroy the fixture, regardless of test result.
     * This is the default behavior (same as current implementation).
     */
    ALWAYS,

    /**
     * Never destroy the fixture after test completion.
     * Useful for debugging or manual inspection of test artifacts.
     */
    NEVER,

    /**
     * Destroy only if the test passed.
     * If the test failed, keep the fixture for post-mortem analysis.
     */
    ON_SUCCESS;

    /**
     * Default cleanup policy: always destroy.
     */
    public static CleanupPolicy defaultPolicy() {
        return ALWAYS;
    }

    /**
     * Determines whether the fixture should be destroyed based on the policy and test outcome.
     *
     * @param testPassed true if the test passed, false if it failed
     * @return true if the fixture should be destroyed, false if it should be retained
     */
    public boolean shouldDestroy(boolean testPassed) {
        return switch (this) {
            case ALWAYS -> true;
            case NEVER -> false;
            case ON_SUCCESS -> testPassed;
        };
    }
}


