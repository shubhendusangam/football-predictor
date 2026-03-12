package com.app.common.logging;

/**
 * Centralized logging constants shared across all modules.
 * <p>
 * Provides consistent MDC keys, logger category names, and log patterns
 * so every microservice in the project logs in a uniform format.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 *   import static com.app.common.logging.LogConstants.MDC_REQUEST_ID;
 *   MDC.put(MDC_REQUEST_ID, uuid);
 * </pre>
 */
public final class LogConstants {

    private LogConstants() {
        // utility class — no instances
    }

    // ── MDC Keys ────────────────────────────────────────────────────────────
    /** Unique correlation ID for each HTTP request (UUID). */
    public static final String MDC_REQUEST_ID = "requestId";

    /** Client IP address. */
    public static final String MDC_CLIENT_IP = "clientIp";

    /** HTTP method (GET, POST, …). */
    public static final String MDC_HTTP_METHOD = "httpMethod";

    /** Request URI path. */
    public static final String MDC_REQUEST_URI = "requestUri";

    /** Name of the Spring application (set from spring.application.name). */
    public static final String MDC_APP_NAME = "appName";

    /** Active Spring profile (dev, docker, test). */
    public static final String MDC_PROFILE = "profile";

    // ── Logger Categories (used in logback-spring.xml & aspects) ────────────
    /** Controllers / REST API layer. */
    public static final String LOGGER_API = "API";

    /** CSV data ingestion pipeline. */
    public static final String LOGGER_INGESTION = "INGESTION";

    /** ML model training & evaluation. */
    public static final String LOGGER_MODEL = "MODEL";

    /** Feature engineering computations. */
    public static final String LOGGER_FEATURES = "FEATURES";

    /** Scheduled / background tasks. */
    public static final String LOGGER_SCHEDULER = "SCHEDULER";

    /** Performance / timing measurements. */
    public static final String LOGGER_PERFORMANCE = "PERFORMANCE";

    // ── Log Patterns ────────────────────────────────────────────────────────
    /** Console pattern with Spring Boot colour support. */
    public static final String CONSOLE_PATTERN =
            "%clr(%d{HH:mm:ss.SSS}){faint} %clr(%-5level) "
            + "%clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} "
            + "%clr(:){faint} [%X{requestId:-system}] %msg%n%throwable";

    /** File pattern — full detail, no colour codes. */
    public static final String FILE_PATTERN =
            "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%t] %-40.40logger{39} "
            + ": [%X{requestId:-system}] [%X{clientIp:-}] %msg%n%throwable";
}

