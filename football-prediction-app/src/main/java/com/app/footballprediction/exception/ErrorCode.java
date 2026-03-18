package com.app.footballprediction.exception;

/**
 * Machine-readable error codes included in every RFC 7807 ProblemDetail response.
 * Clients can switch on {@code errorCode} instead of parsing human-readable messages.
 */
public enum ErrorCode {

    // ── 404 Not Found ───────────────────────────────────────────────────
    PREDICTION_NOT_FOUND("The requested prediction does not exist"),
    RESOURCE_NOT_FOUND("The requested resource does not exist"),

    // ── 400 Bad Request ─────────────────────────────────────────────────
    INVALID_TEAM_NAME("The supplied team name is not recognised"),
    VALIDATION_FAILED("One or more fields failed validation"),
    INVALID_REQUEST("The request is malformed or contains invalid data"),

    // ── 503 Service Unavailable ─────────────────────────────────────────
    MODEL_NOT_TRAINED("The ML model has not been trained yet"),

    // ── 500 Internal Server Error ───────────────────────────────────────
    DATA_SYNC_FAILED("External data synchronisation failed"),
    INTERNAL_ERROR("An unexpected internal error occurred");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

