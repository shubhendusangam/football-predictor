package com.app.footballprediction.config;

import com.app.common.logging.MdcLoggingFilter;

/**
 * Request logging is now handled by the centralized
 * {@link MdcLoggingFilter} registered via
 * {@link com.app.common.logging.LoggingAutoConfiguration}.
 * <p>
 * The centralized filter provides:
 * <ul>
 *   <li>MDC population (requestId, clientIp, httpMethod, requestUri)</li>
 *   <li>Request/response logging with timing</li>
 *   <li>X-Request-ID header propagation</li>
 * </ul>
 *
 * @deprecated Replaced by {@link MdcLoggingFilter} in football-prediction-common.
 *             This class is kept only as a reference and can be safely deleted.
 */
@Deprecated(since = "1.0.0", forRemoval = true)
public class RequestLoggingFilter {
    // Replaced by com.app.common.logging.MdcLoggingFilter
    // See LoggingAutoConfiguration for registration
}