/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.command;

import com.justsyncit.network.NetworkService;
import com.justsyncit.network.TransportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class ServerStartCommandTest {

    @Mock
    private NetworkService networkService;

    @Mock
    private CommandContext context;

    private ServerStartCommand command;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        command = new ServerStartCommand(networkService);
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testExecute_StartsServerSuccessfully() throws Exception {
        when(networkService.isServerRunning()).thenReturn(false).thenReturn(true);
        when(networkService.startServer(anyInt(), any(TransportType.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(context.getNetworkService()).thenReturn(networkService);

        boolean result = command.execute(new String[] { "start", "--port", "8081", "--quiet" }, context);

        assertTrue(result);
        verify(networkService).startServer(eq(8081), eq(TransportType.TCP));
    }

    @Test
    void testExecute_ServerAlreadyRunning() throws Exception {
        when(networkService.isServerRunning()).thenReturn(true);
        when(networkService.getServerPort()).thenReturn(8080);
        when(context.getNetworkService()).thenReturn(networkService);

        boolean result = command.execute(new String[] { "start" }, context);

        assertFalse(result);
        verify(networkService, never()).startServer(anyInt(), any(TransportType.class));
        assertTrue(errContent.toString().contains("Server is already running"));
    }

    @Test
    void testExecute_StartupTimeout() throws Exception {
        when(networkService.isServerRunning()).thenReturn(false);
        // Return a future that never completes (simulating timeout)
        when(networkService.startServer(anyInt(), any(TransportType.class)))
                .thenReturn(new CompletableFuture<>());
        when(context.getNetworkService()).thenReturn(networkService);

        boolean result = command.execute(new String[] { "start", "--quiet" }, context);

        assertFalse(result);
        assertTrue(errContent.toString().contains("Timed out"));
    }
}
