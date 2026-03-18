package com.app.footballprediction.config;

import com.app.footballprediction.exception.DataSyncException;
import com.app.footballprediction.exception.ErrorCode;
import com.app.footballprediction.exception.ModelNotReadyException;
import com.app.footballprediction.exception.ResourceNotFoundException;
import com.app.footballprediction.exception.TeamNotFoundException;
import com.app.footballprediction.exception.ValidationException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Production-grade global exception handler.
 * <p>
 * Every error response uses Spring 6 {@link ProblemDetail} (RFC 7807) and
 * carries a machine-readable {@link ErrorCode} so that API consumers can
 * programmatically identify the failure reason without parsing human text.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404 – Resource Not Found ────────────────────────────────────────

    /**
     * Handle generic resource-not-found exceptions (404).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {} [{}]", ex.getResourceType(), ex.getIdentifier());

        ProblemDetail problem = buildProblem(
                HttpStatus.NOT_FOUND,
                ex.getErrorCode(),
                ex.getMessage()
        );
        problem.setProperty("resourceType", ex.getResourceType());
        problem.setProperty("identifier", ex.getIdentifier());
        return problem;
    }

    // ── 400 – Validation ────────────────────────────────────────────────

    /**
     * Handle custom {@link ValidationException} (400).
     */
    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidationException(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());

        ProblemDetail problem = buildProblem(
                HttpStatus.BAD_REQUEST,
                ex.getErrorCode(),
                ex.getMessage()
        );
        if (!ex.getFieldErrors().isEmpty()) {
            problem.setProperty("fieldErrors", ex.getFieldErrors());
        }
        return problem;
    }

    /**
     * Handle validation errors from {@code @Valid} annotations (400).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Bean validation failed: {}", fieldErrors);

        ProblemDetail problem = buildProblem(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Validation failed"
        );
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /**
     * Handle missing required request parameters (400).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParams(MissingServletRequestParameterException ex) {
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        log.warn("Missing parameter: {}", ex.getParameterName());

        return buildProblem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message);
    }

    /**
     * Handle type mismatches in request parameters (400).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Parameter '%s' should be of type %s",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("Type mismatch: {} - expected {}", ex.getName(), ex.getRequiredType());

        return buildProblem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message);
    }

    /**
     * Handle invalid JSON in request body (400).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleJsonParseError(HttpMessageNotReadableException ex) {
        log.warn("JSON parse error: {}", ex.getMessage());

        ProblemDetail problem = buildProblem(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                "Invalid JSON in request body"
        );
        problem.setProperty("hint", "Check your JSON syntax");
        return problem;
    }

    /**
     * Handle illegal argument exceptions – business logic errors (400).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildProblem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, ex.getMessage());
    }

    // ── Domain-specific exception handlers ──────────────────────────────

    /**
     * Handle team not found – unknown team name supplied by user (400).
     */
    @ExceptionHandler(TeamNotFoundException.class)
    public ProblemDetail handleTeamNotFound(TeamNotFoundException ex) {
        log.warn("Team not found: {}", ex.getTeamName());

        ProblemDetail problem = buildProblem(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_TEAM_NAME,
                ex.getMessage()
        );
        problem.setProperty("hint", "Use GET /api/teams to see valid team names");
        return problem;
    }

    /**
     * Handle model-not-ready – prediction requested before training (503).
     */
    @ExceptionHandler(ModelNotReadyException.class)
    public ProblemDetail handleModelNotReady(ModelNotReadyException ex) {
        log.warn("Model not ready: {}", ex.getMessage());

        ProblemDetail problem = buildProblem(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.MODEL_NOT_TRAINED,
                ex.getMessage()
        );
        problem.setProperty("hint", "Call POST /api/model/train first");
        return problem;
    }

    /**
     * Handle data sync failures (500).
     */
    @ExceptionHandler(DataSyncException.class)
    public ProblemDetail handleDataSyncFailure(DataSyncException ex) {
        log.error("Data sync failed: {}", ex.getMessage(), ex);

        ProblemDetail problem = buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.DATA_SYNC_FAILED,
                ex.getMessage()
        );
        problem.setProperty("hint", "Check external API connectivity and API key configuration");
        return problem;
    }

    // ── HTTP / routing errors ───────────────────────────────────────────

    /**
     * Handle unsupported HTTP methods (405).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod());
        log.warn("Method not supported: {} - Supported: {}", ex.getMethod(), ex.getSupportedHttpMethods());

        ProblemDetail problem = buildProblem(
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorCode.INVALID_REQUEST,
                message
        );
        if (ex.getSupportedHttpMethods() != null) {
            problem.setProperty("supportedMethods", ex.getSupportedHttpMethods().toString());
        }
        return problem;
    }

    /**
     * Handle invalid routes (404).
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ProblemDetail handleNotFound(NoHandlerFoundException ex) {
        log.warn("Endpoint not found: {} {}", ex.getHttpMethod(), ex.getRequestURL());

        ProblemDetail problem = buildProblem(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                String.format("Endpoint '%s' not found", ex.getRequestURL())
        );
        problem.setProperty("hint", "Check the API documentation for available endpoints");
        return problem;
    }

    /**
     * Handle missing static resources (favicon.ico, etc.) – return 404 silently.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Static resource not found: {}", ex.getResourcePath());
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(404), "Resource not found");
    }

    // ── Illegal state / NPE / catch-all ─────────────────────────────────

    /**
     * Handle illegal state exceptions – model not loaded, etc. (503).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());

        ProblemDetail problem = buildProblem(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.INTERNAL_ERROR,
                ex.getMessage()
        );
        problem.setProperty("hint", "The system may still be initializing. Please try again.");
        return problem;
    }

    /**
     * Defensive fallback for null pointer exceptions (500).
     */
    @ExceptionHandler(NullPointerException.class)
    public ProblemDetail handleNullPointer(NullPointerException ex) {
        log.error("NullPointerException: {}", ex.getMessage(), ex);

        ProblemDetail problem = buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred while processing your request"
        );
        problem.setProperty("hint", "Please try again or contact support if the issue persists");
        return problem;
    }

    /**
     * Catch-all handler for any unhandled exceptions (500).
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unhandled exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);

        ProblemDetail problem = buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred"
        );
        problem.setProperty("exceptionType", ex.getClass().getSimpleName());
        problem.setProperty("hint", "Please check the logs for more details");
        return problem;
    }

    // ── Helper ──────────────────────────────────────────────────────────

    /**
     * Build a {@link ProblemDetail} with consistent fields.
     *
     * @param status    HTTP status
     * @param errorCode machine-readable {@link ErrorCode}
     * @param detail    human-readable description
     * @return fully populated ProblemDetail
     */
    private ProblemDetail buildProblem(HttpStatus status, ErrorCode errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("/errors/" + errorCode.name().toLowerCase().replace('_', '-')));
        problem.setProperty("errorCode", errorCode.name());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
