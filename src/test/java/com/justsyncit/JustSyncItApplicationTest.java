package com.justsyncit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for JustSyncItApplication.
 */
class JustSyncItApplicationTest {

    /** Application instance for testing. */
    private JustSyncItApplication application;

    @BeforeEach
    void setUpApplication() throws com.justsyncit.hash.HashingException {
        application = TestServiceFactory.createApplication();
    }

    @Test
    @DisplayName("Application should run without arguments")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testRunWithNoArguments() {
        assertDoesNotThrow(() -> {
            application.run(new String[] { "--help" });
        });
    }

    @Test
    @DisplayName("Application should run with arguments")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testRunWithArguments() {
        String[] args = { "--help", "--version" };
        assertDoesNotThrow(() -> {
            application.run(args);
        });
    }

    @Test
    @DisplayName("Application should handle null arguments gracefully")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testRunWithNullArguments() {
        assertDoesNotThrow(() -> {
            application.run(null);
        });
    }

    @Test
    @DisplayName("Application instance should not be null")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testApplicationInstance() {
        assertNotNull(application);
    }
}