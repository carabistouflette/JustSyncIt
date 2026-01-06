package com.justsyncit.network.quic.tls;

/**
 * Exception thrown when certificate generation fails.
 * Provides specific error handling for certificate operations.
 */
public class CertificateGenerationException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new certificate generation exception.
     *
     * @param message the error message
     */
    public CertificateGenerationException(String message) {
        super(message);
    }

    /**
     * Creates a new certificate generation exception with cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public CertificateGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new certificate generation exception from cause.
     *
     * @param cause the underlying cause
     */
    public CertificateGenerationException(Throwable cause) {
        super(cause);
    }
}