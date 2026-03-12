package com.app.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Centralised logger factory that provides pre-configured SLF4J loggers
 * for well-known application categories (API, Ingestion, Model, etc.).
 * <p>
 * Modules can use either the standard Lombok {@code @Slf4j} annotation
 * <b>or</b> obtain a purpose-specific logger from this class:
 * <pre>
 *   private static final Logger apiLog = AppLoggerFactory.api();
 *   apiLog.info("Endpoint hit");
 * </pre>
 *
 * @see LogConstants
 */
public final class AppLoggerFactory {

    private AppLoggerFactory() {
        // utility class — no instances
    }

    // ── Pre-built category loggers ──────────────────────────────

    /** Logger for REST API / controller layer. */
    public static Logger api() {
        return LoggerFactory.getLogger(LogConstants.LOGGER_API);
    }

    /** Logger for data ingestion pipeline. */
    public static Logger ingestion() {
        return LoggerFactory.getLogger(LogConstants.LOGGER_INGESTION);
    }

    /** Logger for ML model training & evaluation. */
    public static Logger model() {
        return LoggerFactory.getLogger(LogConstants.LOGGER_MODEL);
    }

    /** Logger for feature engineering. */
    public static Logger features() {
        return LoggerFactory.getLogger(LogConstants.LOGGER_FEATURES);
    }

    /** Logger for scheduled/background tasks. */
    public static Logger scheduler() {
        return LoggerFactory.getLogger(LogConstants.LOGGER_SCHEDULER);
    }

    /** Logger for performance measurements. */
    public static Logger performance() {
        return LoggerFactory.getLogger(LogConstants.LOGGER_PERFORMANCE);
    }

    /** Obtain a logger for a specific class (convenience wrapper). */
    public static Logger forClass(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    // ── SLF4J Markers for structured filtering ──────────────────

    /** Marker for audit-trail events (admin actions, security events). */
    public static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

    /** Marker for events related to external API calls. */
    public static final Marker EXTERNAL_API = MarkerFactory.getMarker("EXTERNAL_API");

    /** Marker for data-quality / drift warnings. */
    public static final Marker DATA_QUALITY = MarkerFactory.getMarker("DATA_QUALITY");

    // ── MDC helpers ─────────────────────────────────────────────

    /**
     * Set a custom MDC key-value (cleared automatically by {@link MdcLoggingFilter}).
     */
    public static void putMdc(String key, String value) {
        MDC.put(key, value);
    }

    /** Remove a single MDC key. */
    public static void removeMdc(String key) {
        MDC.remove(key);
    }

    /** Clear all MDC values. */
    public static void clearMdc() {
        MDC.clear();
    }
}

