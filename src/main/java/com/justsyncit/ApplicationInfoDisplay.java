package com.justsyncit;

import com.justsyncit.hash.Blake3Service;

/**
 * Interface for displaying application information.
 * Follows Single Responsibility Principle by focusing only on information
 * display.
 */
public interface ApplicationInfoDisplay {

    /**
     * Displays BLAKE3 implementation information.
     *
     * @param blake3Service the BLAKE3 service
     */
    void displayBlake3Info(Blake3Service blake3Service);

    /**
     * Displays a general information message.
     *
     * @param message the message to display
     */
    void displayInfo(String message);

    /**
     * Displays an error message.
     *
     * @param message the error message to display
     */
    void displayError(String message);
}