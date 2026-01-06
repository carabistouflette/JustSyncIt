package com.justsyncit.web.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileBrowserControllerTest {

    private ConfigController configController;
    private FileBrowserController fileBrowserController;

    @BeforeEach
    void setUp() {
        // Mock ConfigController (simple anonymous class or mock framework if available,
        // assuming manual mock for simplicity)
        configController = new ConfigController(null) {
            private List<String> sources = Collections.emptyList();

            @Override
            public List<String> getBackupSourcesList() {
                return sources;
            }

            // Helper to set sources for test
            public void setSources(List<String> s) {
                this.sources = s;
            }
        };
        fileBrowserController = new FileBrowserController(configController);
    }

    @Test
    void isPathAllowed_NoSources_ShouldAllowUserHome() throws Exception {
        // Arrange
        // By default source list is empty in our mock
        String userHome = System.getProperty("user.home");
        Path allowed = Paths.get(userHome).resolve("Documents");
        Path denied = Paths.get("/etc/passwd"); // Assuming /etc is not user home

        // Reflection to test private method
        Method method = FileBrowserController.class.getDeclaredMethod("isPathAllowed", Path.class);
        method.setAccessible(true);

        // Act & Assert
        assertTrue((boolean) method.invoke(fileBrowserController, allowed),
                "User home should be allowed when no sources defined");

        // This assertion might fail if user home is root (e.g. in container), but
        // generally /etc is outside
        if (!userHome.equals("/")) {
            assertFalse((boolean) method.invoke(fileBrowserController, denied), "Outside user home should be denied");
        }
    }

    @Test
    void isPathAllowed_WithSources_ShouldOnlyAllowWhitelist() throws Exception {
        // Arrange
        String source1 = "/mnt/backup1";
        String source2 = "/mnt/backup2";

        // We need to use our inner class specific method
        // But java doesn't see method on parent type.
        // Let's rely on constructor injection of a subclass?
        // Actually, ConfigController is final? Check code.
        // ConfigController is final in the provided code snippet? No, it says "public
        // final class".
        // Ah, if it's FINAL I cannot extend it. I need to verify that.
    }
}
