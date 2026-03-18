package com.app.footballprediction.exception;

import java.util.Collections;
import java.util.Map;

/**
 * Thrown when request data fails business-level validation.
 * Maps to HTTP 400 in the global exception handler.
 */
public class ValidationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(message);
        this.errorCode = ErrorCode.VALIDATION_FAILED;
        this.fieldErrors = Collections.emptyMap();
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.errorCode = ErrorCode.VALIDATION_FAILED;
        this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyMap();
    }

    public ValidationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = Collections.emptyMap();
    }

    public ValidationException(ErrorCode errorCode, String message, Map<String, String> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyMap();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}

