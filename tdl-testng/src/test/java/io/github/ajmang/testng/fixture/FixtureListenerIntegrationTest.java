package io.github.ajmang.testng.fixture;

import io.github.ajmang.tdl.core.fixture.Fixture;
import io.github.ajmang.tdl.core.fixture.FixtureProvider;
import org.testng.Assert;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests that drive TestNG's {@link FixtureListener} via TestNG API.
 */
class FixtureListenerIntegrationTest {

    @org.junit.jupiter.api.Test
    void fieldInjectionSharesFixtureAcrossTests() {
        TestNG testng = new TestNG();
        testng.setTestClasses(new Class[]{FieldInjectionTest.class});
        TestListenerAdapter listener = new TestListenerAdapter();
        testng.addListener(listener);
        testng.run();

        Assert.assertEquals(listener.getPassedTests().size(), 2, "Both tests should pass");
        Assert.assertEquals(listener.getFailedTests().size(), 0, "No tests should fail");
        Assert.assertEquals(FieldInjectionTest.firstId, FieldInjectionTest.secondId,
                "Field-injected fixture should be shared across tests");
    }

    @org.junit.jupiter.api.Test
    void cleanupPolicyIsHonoredByTestngAdapter() {
        TestNG testng = new TestNG();
        testng.setTestClasses(new Class[]{CleanupPolicyTest.class});
        TestListenerAdapter listener = new TestListenerAdapter();
        testng.addListener(listener);
        testng.run();

        Assert.assertEquals(listener.getPassedTests().size(), 1, "Test should pass");
        Assert.assertTrue(CleanupPolicyProvider.destroyed.get() > 0,
                "ALWAYS cleanup policy should trigger destroy");
    }

    @Listeners(FixtureListener.class)
    public static class FieldInjectionTest {
        private static String firstId;
        private static String secondId;

        @Fixture(provider = CountingProvider.class)
        String resource;

        @Test(priority = 1)
        public void first() {
            Assert.assertNotNull(resource);
            firstId = resource;
        }

        @Test(priority = 2)
        public void second() {
            Assert.assertNotNull(resource);
            secondId = resource;
            Assert.assertEquals(secondId, firstId);
        }
    }

    @Listeners(FixtureListener.class)
    public static class CleanupPolicyTest {
        @Fixture(provider = CleanupPolicyProvider.class)
        String resource;

        @Test
        public void alwaysPolicy() {
            Assert.assertNotNull(resource);
        }
    }

    public static class CountingProvider implements FixtureProvider<String> {
        private static final AtomicInteger counter = new AtomicInteger();

        @Override
        public String create() {
            return "res-" + counter.incrementAndGet();
        }

        @Override
        public void destroy(String instance) {
        }
    }

    public static class CleanupPolicyProvider implements FixtureProvider<String> {
        static final AtomicInteger destroyed = new AtomicInteger();

        @Override
        public String create() {
            return "tracked-" + System.nanoTime();
        }

        @Override
        public void destroy(String instance) {
            destroyed.incrementAndGet();
        }

    }
}
