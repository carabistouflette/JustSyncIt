package com.justsyncit.storage;

import java.io.IOException;

/**
 * Interface for resources that can be closed.
 * Follows Interface Segregation Principle by focusing only on resource cleanup.
 */
public interface ClosableResource extends AutoCloseable {

    /**
     * Closes the resource and releases any associated resources.
     *
     * @throws IOException if an I/O error occurs during closing
     */
    @Override
    void close() throws IOException;
}