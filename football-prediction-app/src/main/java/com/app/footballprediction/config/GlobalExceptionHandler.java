package com.app.footballprediction.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.app.footballprediction.exception.DataSyncException;
import com.app.footballprediction.exception.ModelNotReadyException;
import com.app.footballprediction.exception.TeamNotFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for consistent error responses across all controllers.
 *
 * Provides:
 * - Standardized error response format
 * - Proper HTTP status codes
 * - Detailed error logging
 * - User-friendly error messages
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle validation errors from @Valid annotations.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Validation failed: {}", fieldErrors);

        return ResponseEntity.badRequest().body(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Validation failed",
            fieldErrors
        ));
    }

    /**
     * Handle missing required request parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParams(MissingServletRequestParameterException ex) {
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        log.warn("Missing parameter: {}", ex.getParameterName());

        return ResponseEntity.badRequest().body(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            message,
            null
        ));
    }

    /**
     * Handle type mismatches in request parameters.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Parameter '%s' should be of type %s",
            ex.getName(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("Type mismatch: {} - expected {}", ex.getName(), ex.getRequiredType());

        return ResponseEntity.badRequest().body(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            message,
            null
        ));
    }

    /**
     * Handle invalid JSON in request body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJsonParseError(HttpMessageNotReadableException ex) {
        log.warn("JSON parse error: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid JSON in request body",
            Map.of("hint", "Check your JSON syntax")
        ));
    }

    /**
     * Handle unsupported HTTP methods.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod());
        log.warn("Method not supported: {} - Supported: {}", ex.getMethod(), ex.getSupportedHttpMethods());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(buildErrorResponse(
            HttpStatus.METHOD_NOT_ALLOWED,
            message,
            Map.of("supportedMethods", ex.getSupportedHttpMethods() != null
                ? ex.getSupportedHttpMethods().toString()
                : "none")
        ));
    }

    /**
     * Handle invalid routes (404).
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoHandlerFoundException ex) {
        log.warn("Endpoint not found: {} {}", ex.getHttpMethod(), ex.getRequestURL());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildErrorResponse(
            HttpStatus.NOT_FOUND,
            String.format("Endpoint '%s' not found", ex.getRequestURL()),
            Map.of("hint", "Check the API documentation for available endpoints")
        ));
    }

    /**
     * Handle missing static resources (favicon.ico, etc.) - return 404 silently.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex) {
        // Silently return 404 for missing static resources like favicon.ico
        // Don't log error level to reduce noise
        log.debug("Static resource not found: {}", ex.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    // ── Domain-specific exception handlers ─────────────────────────────

    /**
     * Handle team not found (unknown team name supplied by user).
     */
    @ExceptionHandler(TeamNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTeamNotFound(TeamNotFoundException ex) {
        log.warn("Team not found: {}", ex.getTeamName());
        return ResponseEntity.badRequest().body(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            ex.getMessage(),
            Map.of("hint", "Use GET /api/teams to see valid team names")
        ));
    }

    /**
     * Handle model-not-ready (prediction requested before training).
     */
    @ExceptionHandler(ModelNotReadyException.class)
    public ResponseEntity<Map<String, Object>> handleModelNotReady(ModelNotReadyException ex) {
        log.warn("Model not ready: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(buildErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE,
            ex.getMessage(),
            Map.of("hint", "Call POST /api/model/train first")
        ));
    }

    /**
     * Handle data sync failures.
     */
    @ExceptionHandler(DataSyncException.class)
    public ResponseEntity<Map<String, Object>> handleDataSyncFailure(DataSyncException ex) {
        log.error("Data sync failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage(),
            Map.of("hint", "Check external API connectivity and API key configuration")
        ));
    }

    /**
     * Handle illegal argument exceptions (business logic errors).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            ex.getMessage(),
            null
        ));
    }

    /**
     * Handle illegal state exceptions (model not loaded, etc.).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(buildErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE,
            ex.getMessage(),
            Map.of("hint", "The system may still be initializing. Please try again.")
        ));
    }

    /**
     * Handle null pointer exceptions (defensive fallback).
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointer(NullPointerException ex) {
        log.error("NullPointerException: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred while processing your request",
            Map.of("hint", "Please try again or contact support if the issue persists")
        ));
    }

    /**
     * Catch-all handler for any unhandled exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred",
            Map.of(
                "type", ex.getClass().getSimpleName(),
                "hint", "Please check the logs for more details"
            )
        ));
    }

    /**
     * Build a standardized error response.
     */
    private Map<String, Object> buildErrorResponse(HttpStatus status, String message, Object details) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("message", message);

        if (details != null) {
            response.put("details", details);
        }

        return response;
    }
}

