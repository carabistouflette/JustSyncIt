package com.justsyncit;

/**
 * Exception thrown when service operations fail.
 */
public class ServiceException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new ServiceException with the specified message.
     *
     * @param message detail message
     */
    public ServiceException(String message) {
        super(message);
    }

    /**
     * Creates a new ServiceException with the specified message and cause.
     *
     * @param message detail message
     * @param cause   cause of this exception
     */
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}