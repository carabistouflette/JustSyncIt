package com.justsyncit.scanner;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Interface for visiting files and directories during filesystem scanning.
 * Follows Interface Segregation Principle by providing focused visit
 * operations.
 */
public interface FileVisitor {

    /**
     * Result types for file visitation.
     */
    enum FileVisitResult {
        /** Continue processing. */
        CONTINUE,
        /** Skip this file/directory. */
        SKIP,
        /** Skip this subtree. */
        SKIP_SUBTREE,
        /** Terminate scanning. */
        TERMINATE
    }

    /**
     * Called when a file is visited during scanning.
     *
     * @param file  the file that was visited
     * @param attrs the file attributes
     * @return the result indicating how to continue processing
     * @throws IOException if an I/O error occurs
     */
    FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException;

    /**
     * Called when a directory is visited during scanning.
     *
     * @param dir   the directory that was visited
     * @param attrs the directory attributes
     * @return the result indicating how to continue processing
     * @throws IOException if an I/O error occurs
     */
    FileVisitResult visitDirectory(Path dir, BasicFileAttributes attrs) throws IOException;

    /**
     * Called when a file or directory cannot be visited.
     *
     * @param file the file that could not be visited
     * @param exc  the exception that prevented visitation
     * @return the result indicating how to continue processing
     * @throws IOException if an I/O error occurs
     */
    FileVisitResult visitFailed(Path file, IOException exc) throws IOException;
}