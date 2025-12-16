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
import com.justsyncit.storage.ContentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class NetworkCommandTest {

    @Mock
    private NetworkService networkService;

    @Mock
    private CommandContext context;

    @Mock
    private ContentStore contentStore;

    private com.justsyncit.network.command.NetworkCommand command;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        command = com.justsyncit.network.command.NetworkCommand.create(networkService, contentStore);
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        when(context.getNetworkService()).thenReturn(networkService);
        when(context.getContentStore()).thenReturn(contentStore);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testStart_UnwrapsCompletionException() throws Exception {
        // Arrange
        String underlyingError = "Bind failed: Address already in use";
        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException(underlyingError));

        when(networkService.startServer(anyInt())).thenReturn(failedFuture);

        // Act
        boolean result = command.execute(new String[] { "start", "8080" }, context);

        // Assert
        assertTrue(result); // Command execution itself returns true (logic flowed), but we check output for
                            // error
        // Note: The execute method in NetworkCommand returns true if the command
        // *parsing* and dispatch was successful.
        // The *operation* might fail asynchronously.
        // Wait, looking at NetworkCommand.execute:
        // catches Exception and returns false.
        // handleStart returns void.
        // inside handleStart, it calls join().
        // So the exception will propagate out of join() as CompletionException to
        // handleStart?
        // No, the .exceptionally() block swallows it and returns null!
        // So handleStart finishes normally.

        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Failed to start network server: " + underlyingError),
                "Error output should contain unwrapped message. Actual: " + errOutput);
    }

    @Test
    void testStart_UnwrapsNestedCompletionException() throws Exception {
        // Arrange
        String underlyingError = "Nested error";
        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new CompletionException(new RuntimeException(underlyingError)));

        when(networkService.startServer(anyInt())).thenReturn(failedFuture);

        // Act
        command.execute(new String[] { "start", "8080" }, context);

        // Assert
        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Failed to start network server: " + underlyingError),
                "Error output should contain unwrapped message. Actual: " + errOutput);
    }
}
