package com.justsyncit.storage.metadata;

import com.justsyncit.storage.ClosableResource;

import java.io.IOException;

/**
 * Interface for database transactions.
 * Follows Interface Segregation Principle by focusing only on transaction
 * management.
 */
public interface Transaction extends ClosableResource {

    /**
     * Commits the transaction, making all changes permanent.
     *
     * @throws IOException           if the commit fails
     * @throws IllegalStateException if the transaction is already completed
     */
    void commit() throws IOException;

    /**
     * Rolls back the transaction, undoing all changes.
     *
     * @throws IOException           if the rollback fails
     * @throws IllegalStateException if the transaction is already completed
     */
    void rollback() throws IOException;

    /**
     * Checks if the transaction is active (not yet committed or rolled back).
     *
     * @return true if the transaction is active, false otherwise
     */
    boolean isActive();
}