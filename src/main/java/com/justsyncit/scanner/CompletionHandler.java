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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

/**
 * Callback interface for handling the completion of asynchronous operations.
 * This follows the CompletionHandler pattern from Java NIO.2 AsynchronousFileChannel.
 * 
 * @param <T> the result type of the asynchronous operation
 * @param <E> the exception type that may be thrown during the operation
 */
public interface CompletionHandler<T, E extends Exception> {
    
    /**
     * Invoked when the asynchronous operation completes successfully.
     *
     * @param result the result of the operation
     */
    void completed(T result);
    
    /**
     * Invoked when the asynchronous operation fails.
     *
     * @param exception the exception that caused the failure
     */
    void failed(E exception);
}