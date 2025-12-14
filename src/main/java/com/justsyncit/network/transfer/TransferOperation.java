/*
* JustSyncIt - Backup solution
* Copyright (C) 2023 JustSyncIt Team
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/

package com.justsyncit.network.transfer;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * Represents a transfer operation (either a buffer or a file region).
 */
public interface TransferOperation {
    /**
     * Performs the transfer to the given channel.
     * 
     * @param channel the channel to write to
     * @return true if the operation is complete, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean transfer(SocketChannel channel) throws IOException;

    /**
     * Releases any resources associated with this operation.
     */
    void release();
}
