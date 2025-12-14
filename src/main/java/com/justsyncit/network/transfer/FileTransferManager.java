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

package com.justsyncit.network.transfer;

import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.storage.ContentStore;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for managing file transfers over the network.
 * Follows the Interface Segregation Principle by providing focused methods
 * for file transfer operations.
 */
public interface FileTransferManager {

        /**
         * Sets the network service to use for transfers.
         * 
         * @param networkService the network service
         */
        void setNetworkService(com.justsyncit.network.NetworkService networkService);

        /**
         * Sets the compression service to use for transfers.
         * 
         * @param compressionService the compression service
         */
        void setCompressionService(com.justsyncit.network.compression.CompressionService compressionService);

        /**
         * Starts the file transfer manager.
         *
         * @return a CompletableFuture that completes when the manager is started
         */
        CompletableFuture<Void> start();

        /**
         * Stops the file transfer manager.
         *
         * @return a CompletableFuture that completes when the manager is stopped
         */
        CompletableFuture<Void> stop();

        /**
         * Sends a file to a remote node.
         *
         * @param filePath      the path to the file to send
         * @param remoteAddress the remote address
         * @param contentStore  the content store for chunk access
         * @return a CompletableFuture that completes when the transfer is finished
         */
        CompletableFuture<FileTransferResult> sendFile(Path filePath, InetSocketAddress remoteAddress,
                        ContentStore contentStore);

        /**
         * Handles an incoming file transfer request.
         *
         * @param request       the file transfer request message
         * @param remoteAddress the remote address
         * @param contentStore  the content store for storing chunks
         * @return a CompletableFuture that completes when the response is sent
         */
        CompletableFuture<Void> handleFileTransferRequest(ProtocolMessage request, InetSocketAddress remoteAddress,
                        ContentStore contentStore);

        /**
         * Handles chunk data for an incoming file transfer.
         *
         * @param chunkData     the chunk data message
         * @param remoteAddress the remote address
         * @param contentStore  the content store for storing chunks
         * @return a CompletableFuture that completes when the chunk is processed
         */
        CompletableFuture<Void> handleChunkData(ProtocolMessage chunkData, InetSocketAddress remoteAddress,
                        ContentStore contentStore);

        /**
         * Handles a chunk acknowledgment.
         *
         * @param chunkAck      the chunk acknowledgment message
         * @param remoteAddress the remote address
         * @return a CompletableFuture that completes when the acknowledgment is
         *         processed
         */
        CompletableFuture<Void> handleChunkAck(ProtocolMessage chunkAck, InetSocketAddress remoteAddress);

        /**
         * Handles a transfer complete message.
         *
         * @param completeMessage the transfer complete message
         * @param remoteAddress   the remote address
         * @return a CompletableFuture that completes when the message is processed
         */
        CompletableFuture<Void> handleTransferComplete(ProtocolMessage completeMessage,
                        InetSocketAddress remoteAddress);

        /**
         * Cancels an ongoing file transfer.
         *
         * @param transferId the transfer ID
         * @return a CompletableFuture that completes when the transfer is cancelled
         */
        CompletableFuture<Void> cancelTransfer(String transferId);

        /**
         * Gets the status of a file transfer.
         *
         * @param transferId the transfer ID
         * @return the transfer status, or null if not found
         */
        FileTransferStatus getTransferStatus(String transferId);

        /**
         * Gets all active transfers.
         *
         * @return list of active transfers
         */
        List<FileTransferStatus> getActiveTransfers();

        /**
         * Gets the number of active transfers.
         *
         * @return the number of active transfers
         */
        int getActiveTransferCount();

        /**
         * Registers a transfer event listener.
         *
         * @param listener the event listener
         */
        void addTransferEventListener(TransferEventListener listener);

        /**
         * Unregisters a transfer event listener.
         *
         * @param listener the event listener
         */
        void removeTransferEventListener(TransferEventListener listener);

        /**
         * Interface for listening to transfer events.
         */
        interface TransferEventListener {
                /**
                 * Called when a transfer starts.
                 *
                 * @param filePath      the file path
                 * @param remoteAddress the remote address
                 * @param fileSize      the file size
                 */
                void onTransferStarted(Path filePath, InetSocketAddress remoteAddress, long fileSize);

                /**
                 * Called when transfer progress is made.
                 *
                 * @param filePath         the file path
                 * @param remoteAddress    the remote address
                 * @param bytesTransferred the number of bytes transferred
                 * @param totalBytes       the total number of bytes
                 */
                void onTransferProgress(Path filePath, InetSocketAddress remoteAddress, long bytesTransferred,
                                long totalBytes);

                /**
                 * Called when a transfer completes.
                 *
                 * @param filePath      the file path
                 * @param remoteAddress the remote address
                 * @param success       true if successful, false otherwise
                 * @param errorMessage  the error message, or null if successful
                 */
                void onTransferCompleted(Path filePath, InetSocketAddress remoteAddress, boolean success,
                                String errorMessage);

                /**
                 * Called when an error occurs.
                 *
                 * @param error   the error
                 * @param context the context
                 */
                void onError(Throwable error, String context);
        }
}