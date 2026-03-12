package com.app.footballprediction.exception;

/**
 * Thrown when an external data sync operation fails.
 */
public class DataSyncException extends RuntimeException {

    public DataSyncException(String message) {
        super(message);
    }

    public DataSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}

