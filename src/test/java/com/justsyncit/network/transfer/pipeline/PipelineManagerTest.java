package com.justsyncit.network.transfer.pipeline;

import org.junit.jupiter.api.Test;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineManagerTest {

    @Test
    void testInitialization() {
        PipelineManager manager = new PipelineManager();

        assertNotNull(manager.getExecutor());
        assertTrue(manager.getExecutor() instanceof ForkJoinPool);
        assertTrue(manager.getParallelism() >= 4);

        manager.shutdown();
        assertTrue(manager.getExecutor().isShutdown());
    }
}
