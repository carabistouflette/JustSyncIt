package com.justsyncit.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetworkConfigurationTest {

    @Test
    void testDefaultValues() {
        NetworkConfiguration config = new NetworkConfiguration();

        assertEquals(4 * 1024 * 1024, config.getSendBufferSize());
        assertEquals(4 * 1024 * 1024, config.getReceiveBufferSize());
        assertEquals(30_000, config.getConnectTimeoutMs());
        assertTrue(config.isTcpNoDelay());
        assertTrue(config.isKeepAlive());
        assertTrue(config.isReuseAddress());
    }

    @Test
    void testCustomValues() {
        NetworkConfiguration config = new NetworkConfiguration(
                1024, 2048, 5000, false, false, false);

        assertEquals(1024, config.getSendBufferSize());
        assertEquals(2048, config.getReceiveBufferSize());
        assertEquals(5000, config.getConnectTimeoutMs());
        assertFalse(config.isTcpNoDelay());
        assertFalse(config.isKeepAlive());
        assertFalse(config.isReuseAddress());
    }
}
