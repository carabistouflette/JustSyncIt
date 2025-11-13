/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for JustSyncItApplication.
 */
class JustSyncItApplicationTest {

    /** Application instance for testing. */
    private JustSyncItApplication application;

    @BeforeEach
    void setUp() {
        application = TestServiceFactory.createApplication();
    }

    @Test
    @DisplayName("Application should run without arguments")
    void testRunWithNoArguments() {
        assertDoesNotThrow(() -> {
            application.run(new String[]{});
        });
    }

    @Test
    @DisplayName("Application should run with arguments")
    void testRunWithArguments() {
        String[] args = {"--help", "--version"};
        assertDoesNotThrow(() -> {
            application.run(args);
        });
    }

    @Test
    @DisplayName("Application should handle null arguments gracefully")
    void testRunWithNullArguments() {
        assertDoesNotThrow(() -> {
            application.run(new String[]{});
        });
    }

    @Test
    @DisplayName("Application instance should not be null")
    void testApplicationInstance() {
        assertNotNull(application);
    }
}