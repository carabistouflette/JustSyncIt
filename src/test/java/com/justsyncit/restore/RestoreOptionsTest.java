package com.justsyncit.restore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for RestoreOptions.
 */
public class RestoreOptionsTest {

    private RestoreOptions.Builder builder;

    @BeforeEach
    void setUp() {
        builder = new RestoreOptions.Builder();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testDefaultOptions() {
        RestoreOptions options = builder.build();
        assertFalse(options.isOverwriteExisting());
        assertFalse(options.isBackupExisting());
        assertTrue(options.isVerifyIntegrity());
        assertTrue(options.isPreserveAttributes());
        assertNull(options.getIncludePattern());
        assertNull(options.getExcludePattern());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBuilderWithOverwriteExisting() {
        RestoreOptions options = builder.overwriteExisting(true).build();
        assertTrue(options.isOverwriteExisting());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBuilderWithBackupExisting() {
        RestoreOptions options = builder.backupExisting(true).build();
        assertTrue(options.isBackupExisting());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBuilderWithVerifyIntegrity() {
        RestoreOptions options = builder.verifyIntegrity(false).build();
        assertFalse(options.isVerifyIntegrity());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBuilderWithPreserveAttributes() {
        RestoreOptions options = builder.preserveAttributes(false).build();
        assertFalse(options.isPreserveAttributes());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBuilderWithIncludePattern() {
        RestoreOptions options = builder.includePattern("*.txt").build();
        assertEquals("*.txt", options.getIncludePatternString());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBuilderWithExcludePattern() {
        RestoreOptions options = builder.excludePattern("*.tmp").build();
        assertEquals("*.tmp", options.getExcludePatternString());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBuilderWithMultipleOptions() {
        RestoreOptions options = builder
                .overwriteExisting(true)
                .backupExisting(false)
                .verifyIntegrity(false)
                .preserveAttributes(true)
                .includePattern("*.java")
                .excludePattern("*.class")
                .build();
        assertTrue(options.isOverwriteExisting());
        assertFalse(options.isBackupExisting());
        assertFalse(options.isVerifyIntegrity());
        assertTrue(options.isPreserveAttributes());
        assertEquals("*.java", options.getIncludePatternString());
        assertEquals("*.class", options.getExcludePatternString());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testToString() {
        RestoreOptions options = builder
                .overwriteExisting(true)
                .includePattern("*.txt")
                .build();
        String result = options.toString();
        assertNotNull(result);
        assertTrue(result.contains("overwriteExisting=true"));
        assertTrue(result.contains("*.txt"));
    }
}