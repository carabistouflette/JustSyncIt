package com.justsyncit.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class BackupExceptionTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testDefaultConstructor() {
        BackupException exception = new BackupException("Something went wrong");
        assertEquals("Something went wrong", exception.getMessage());
        assertEquals(BackupException.ErrorCode.UNKNOWN, exception.getErrorCode());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testConstructorWithCause() {
        Throwable cause = new RuntimeException("Root cause");
        BackupException exception = new BackupException("Wrapper", cause);
        assertEquals("Wrapper", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(BackupException.ErrorCode.UNKNOWN, exception.getErrorCode());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testConstructorWithErrorCode() {
        BackupException exception = new BackupException("Config error", BackupException.ErrorCode.CONFIGURATION_ERROR);
        assertEquals("Config error", exception.getMessage());
        assertEquals(BackupException.ErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testFullConstructor() {
        Throwable cause = new IllegalArgumentException("Bad arg");
        BackupException exception = new BackupException("IO failed", cause, BackupException.ErrorCode.IO_ERROR);
        assertEquals("IO failed", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(BackupException.ErrorCode.IO_ERROR, exception.getErrorCode());
    }
}
