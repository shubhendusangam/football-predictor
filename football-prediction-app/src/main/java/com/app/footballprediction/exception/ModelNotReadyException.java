package com.app.footballprediction.exception;

/**
 * Thrown when the ML model is not loaded/trained and a prediction is requested.
 */
public class ModelNotReadyException extends RuntimeException {

    public ModelNotReadyException() {
        super("Model is not loaded. Call POST /api/model/train first.");
    }

    public ModelNotReadyException(String message) {
        super(message);
    }
}

