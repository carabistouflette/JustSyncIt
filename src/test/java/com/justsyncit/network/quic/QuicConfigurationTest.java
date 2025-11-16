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

package com.justsyncit.network.quic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

/**
 * Unit tests for QuicConfiguration.
 */
@DisplayName("QuicConfiguration Tests")
public class QuicConfigurationTest {

    private QuicConfiguration.Builder builder;

    @BeforeEach
    void setUp() {
        builder = QuicConfiguration.builder();
    }

    @Test
    @DisplayName("Default configuration should have sensible values")
    void testDefaultConfiguration() {
        QuicConfiguration config = QuicConfiguration.defaultConfiguration();
        
        assertEquals(Duration.ofSeconds(30), config.getIdleTimeout(), "Default idle timeout should be 30 seconds");
        assertEquals(100, config.getMaxBidirectionalStreams(), "Default max bidirectional streams should be 100");
        assertEquals(100, config.getMaxUnidirectionalStreams(), "Default max unidirectional streams should be 100");
        assertEquals(1024 * 1024, config.getInitialMaxStreamData(), "Default max stream data should be 1MB");
        assertEquals(false, config.isConnectionMigration(), "Connection migration should be disabled by default");
        assertTrue(config.isZeroRttSupport(), "0-RTT should be enabled by default");
        assertNotNull(config.getTlsConfiguration(), "TLS configuration should not be null");
    }

    @Test
    @DisplayName("Builder should create configuration with custom values")
    void testBuilderWithCustomValues() {
        QuicConfiguration config = builder
            .idleTimeout(Duration.ofMinutes(5))
            .maxBidirectionalStreams(200)
            .maxUnidirectionalStreams(200)
            .initialMaxStreamData(2 * 1024 * 1024)
            .connectionMigration(false)
            .zeroRttSupport(false)
            .build();

        assertEquals(Duration.ofMinutes(5), config.getIdleTimeout(), "Custom idle timeout should be set");
        assertEquals(200, config.getMaxBidirectionalStreams(), "Custom max bidirectional streams should be set");
        assertEquals(200, config.getMaxUnidirectionalStreams(), "Custom max unidirectional streams should be set");
        assertEquals(2 * 1024 * 1024, config.getInitialMaxStreamData(), "Custom max stream data should be set");
        assertFalse(config.isConnectionMigration(), "Connection migration should be disabled");
        assertFalse(config.isZeroRttSupport(), "0-RTT should be disabled");
        assertNotNull(config.getTlsConfiguration(), "TLS configuration should not be null");
    }

    @Test
    @DisplayName("Builder should validate idle timeout")
    void testIdleTimeoutValidation() {
        assertThrows(IllegalArgumentException.class, () -> 
            builder.idleTimeout(Duration.ofMillis(-1)),
            "Negative idle timeout should throw exception");
        
        assertThrows(IllegalArgumentException.class, () -> 
            builder.idleTimeout(Duration.ZERO),
            "Zero idle timeout should throw exception");
        
        // Should not throw for positive duration
        assertDoesNotThrow(() -> 
            builder.idleTimeout(Duration.ofMillis(1)),
            "Positive idle timeout should not throw exception");
    }

    @Test
    @DisplayName("Builder should validate max streams")
    void testMaxStreamsValidation() {
        assertThrows(IllegalArgumentException.class, () -> 
            builder.maxBidirectionalStreams(0),
            "Zero max streams should throw exception");
        
        assertThrows(IllegalArgumentException.class, () -> 
            builder.maxBidirectionalStreams(-1),
            "Negative max streams should throw exception");
        
        // Should not throw for positive value
        assertDoesNotThrow(() -> 
            builder.maxBidirectionalStreams(1),
            "Positive max streams should not throw exception");
    }

    @Test
    @DisplayName("Builder should validate max stream data")
    void testMaxStreamDataValidation() {
        assertThrows(IllegalArgumentException.class, () -> 
            builder.initialMaxStreamData(0),
            "Zero max stream data should throw exception");
        
        assertThrows(IllegalArgumentException.class, () -> 
            builder.initialMaxStreamData(-1),
            "Negative max stream data should throw exception");
        
        // Should not throw for positive value
        assertDoesNotThrow(() -> 
            builder.initialMaxStreamData(1),
            "Positive max stream data should not throw exception");
    }

    @Test
    @DisplayName("Configuration should be immutable")
    void testConfigurationImmutability() {
        QuicConfiguration original = QuicConfiguration.defaultConfiguration();
        
        // Create a new configuration with different values
        QuicConfiguration modified = QuicConfiguration.builder()
            .idleTimeout(Duration.ofMinutes(10))
            .build();
        
        // Original should be unchanged
        assertNotEquals(modified.getIdleTimeout(), original.getIdleTimeout(),
            "Original configuration should be unchanged");
    }

    @Test
    @DisplayName("Configuration should have meaningful toString")
    void testToString() {
        QuicConfiguration config = QuicConfiguration.defaultConfiguration();
        String str = config.toString();
        
        assertNotNull(str, "toString should not return null");
        assertTrue(str.contains("QuicConfiguration"), "toString should contain class name");
        assertTrue(str.contains("idleTimeout"), "toString should contain idle timeout");
        assertTrue(str.contains("maxBidirectionalStreams"), "toString should contain max bidirectional streams");
        assertTrue(str.contains("maxUnidirectionalStreams"), "toString should contain max unidirectional streams");
    }

    @Test
    @DisplayName("Configuration should support TLS configuration")
    void testTlsConfiguration() {
        QuicTlsConfiguration tlsConfig = QuicTlsConfiguration.defaultConfiguration();
        QuicConfiguration config = builder
            .tlsConfiguration(tlsConfig)
            .build();
        
        assertSame(tlsConfig, config.getTlsConfiguration(), 
            "TLS configuration should be set correctly");
    }
}