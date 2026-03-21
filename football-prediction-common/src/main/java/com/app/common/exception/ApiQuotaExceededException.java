package com.app.common.exception;

/**
 * Thrown when the API-Football daily quota is exhausted.
 */
public class ApiQuotaExceededException extends RuntimeException {

    public ApiQuotaExceededException(String message) {
        super(message);
    }

    public ApiQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}

