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
