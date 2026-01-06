package com.justsyncit.scanner;

/**
 * Callback interface for handling the completion of asynchronous operations.
 * This follows the CompletionHandler pattern from Java NIO.2
 * AsynchronousFileChannel.
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