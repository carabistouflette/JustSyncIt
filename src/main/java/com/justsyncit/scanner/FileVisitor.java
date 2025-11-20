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

package com.justsyncit.scanner;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Interface for visiting files and directories during filesystem scanning.
 * Follows Interface Segregation Principle by providing focused visit operations.
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
     * @param file the file that was visited
     * @param attrs the file attributes
     * @return the result indicating how to continue processing
     * @throws IOException if an I/O error occurs
     */
    FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException;

    /**
     * Called when a directory is visited during scanning.
     *
     * @param dir the directory that was visited
     * @param attrs the directory attributes
     * @return the result indicating how to continue processing
     * @throws IOException if an I/O error occurs
     */
    FileVisitResult visitDirectory(Path dir, BasicFileAttributes attrs) throws IOException;

    /**
     * Called when a file or directory cannot be visited.
     *
     * @param file the file that could not be visited
     * @param exc the exception that prevented visitation
     * @return the result indicating how to continue processing
     * @throws IOException if an I/O error occurs
     */
    FileVisitResult visitFailed(Path file, IOException exc) throws IOException;
}