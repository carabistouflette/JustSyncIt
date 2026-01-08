package com.justsyncit.web.dto;

/**
 * DTO for API error responses.
 */
public final class ApiError {

    private int status;
    private String error;
    private String message;
    private String path;
    private long timestamp;

    public ApiError() {
        this.timestamp = System.currentTimeMillis();
    }

    public static ApiError of(int status, String error, String message, String path) {
        ApiError apiError = new ApiError();
        apiError.setStatus(status);
        apiError.setError(error);
        apiError.setMessage(message);
        apiError.setPath(path);
        return apiError;
    }

    public static ApiError badRequest(String message, String path) {
        return of(400, "Bad Request", message, path);
    }

    public static ApiError notFound(String message, String path) {
        return of(404, "Not Found", message, path);
    }

    public static ApiError internalError(String message, String path) {
        return of(500, "Internal Server Error", message, path);
    }

    public static ApiError forbidden(String message, String path) {
        return of(403, "Forbidden", message, path);
    }

    public static ApiError conflict(String message, String path) {
        return of(409, "Conflict", message, path);
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
