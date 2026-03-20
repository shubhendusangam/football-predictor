package com.app.footballprediction.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structured request/response logging filter.
 * <p>
 * Logs every non-static HTTP request with:
 * <ul>
 *   <li>HTTP method and URI</li>
 *   <li>Response status code</li>
 *   <li>Duration in milliseconds</li>
 *   <li>Authenticated user identity</li>
 *   <li>Request ID (from MDC, set by {@link com.app.common.logging.MdcLoggingFilter})</li>
 * </ul>
 * <p>
 * <b>Security:</b> Never logs passwords, JWT tokens, API keys, or PII.
 * Authorization headers and query parameters containing sensitive keys are redacted.
 * <p>
 * Runs after the MDC filter (which has HIGHEST_PRECEDENCE) so MDC data is available.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /** Headers that must never be logged (case-insensitive matching). */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie",
            "x-auth-token", "x-api-key", "proxy-authorization"
    );

    /** Query parameter names whose values must be redacted. */
    private static final Pattern SENSITIVE_PARAM_PATTERN = Pattern.compile(
            "(?i)(password|passwd|secret|token|apikey|api_key|api-key|jwt|access_token|refresh_token|credential)"
    );

    /** Static resource prefixes to skip logging. */
    private static final Set<String> STATIC_PREFIXES = Set.of(
            "/css/", "/js/", "/images/", "/fonts/", "/webjars/", "/assets/", "/favicon"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // Skip static resources
        String uri = httpReq.getRequestURI();
        if (isStaticResource(uri)) {
            chain.doFilter(request, response);
            return;
        }

        long startTime = System.nanoTime();

        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            int status = httpResp.getStatus();
            String method = httpReq.getMethod();
            String userId = MDC.get("userId");
            String requestId = MDC.get("requestId");
            String queryString = sanitizeQueryString(httpReq.getQueryString());

            String fullUri = queryString != null ? uri + "?" + queryString : uri;

            if (status >= 500) {
                log.error("HTTP {} {} -> {} ({}ms) [user={}] [reqId={}]",
                        method, fullUri, status, durationMs,
                        userId != null ? userId : "anonymous",
                        requestId != null ? requestId : "-");
            } else if (status >= 400) {
                log.warn("HTTP {} {} -> {} ({}ms) [user={}] [reqId={}]",
                        method, fullUri, status, durationMs,
                        userId != null ? userId : "anonymous",
                        requestId != null ? requestId : "-");
            } else {
                log.info("HTTP {} {} -> {} ({}ms) [user={}] [reqId={}]",
                        method, fullUri, status, durationMs,
                        userId != null ? userId : "anonymous",
                        requestId != null ? requestId : "-");
            }
        }
    }

    /**
     * Redact sensitive parameter values from query strings.
     * e.g., "name=foo&password=secret123" → "name=foo&password=***"
     */
    private String sanitizeQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return null;
        }
        StringBuilder sanitized = new StringBuilder();
        for (String pair : queryString.split("&")) {
            if (!sanitized.isEmpty()) {
                sanitized.append("&");
            }
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0) {
                String key = pair.substring(0, eqIdx);
                if (SENSITIVE_PARAM_PATTERN.matcher(key).find()) {
                    sanitized.append(key).append("=***");
                } else {
                    sanitized.append(pair);
                }
            } else {
                sanitized.append(pair);
            }
        }
        return sanitized.toString();
    }

    private boolean isStaticResource(String uri) {
        for (String prefix : STATIC_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        return uri.endsWith(".ico") || uri.endsWith(".map")
                || uri.endsWith(".html") || uri.endsWith(".json")
                && !uri.startsWith("/api/");
    }
}