package com.app.common.logging;

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

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that populates SLF4J MDC (Mapped Diagnostic Context)
 * with request-scoped data, enabling every log line to carry correlation info.
 * <p>
 * Populated MDC keys (see {@link LogConstants}):
 * <ul>
 *   <li>{@code requestId}  - UUID generated per request (or from X-Request-ID header)</li>
 *   <li>{@code traceId}    - distributed trace ID (from header or generated)</li>
 *   <li>{@code spanId}     - span ID (from header or generated)</li>
 *   <li>{@code userId}     - authenticated user identity (from SecurityContext)</li>
 *   <li>{@code clientIp}   - caller's remote address</li>
 *   <li>{@code httpMethod} - GET / POST / ...</li>
 *   <li>{@code requestUri} - the URI path</li>
 * </ul>
 * <p>
 * Also logs request entry/exit with response status and elapsed time.
 * <p>
 * Register this filter as a Spring Component in the module that
 * handles HTTP traffic, or via a FilterRegistrationBean.
 *
 * @see LogConstants
 */
public class MdcLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(MdcLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // Populate MDC
        String requestId = resolveRequestId(httpReq);
        MDC.put(LogConstants.MDC_REQUEST_ID, requestId);
        MDC.put(LogConstants.MDC_CLIENT_IP, resolveClientIp(httpReq));
        MDC.put(LogConstants.MDC_HTTP_METHOD, httpReq.getMethod());
        MDC.put(LogConstants.MDC_REQUEST_URI, httpReq.getRequestURI());

        // Distributed tracing: traceId and spanId
        String traceId = resolveHeader(httpReq, "X-Trace-ID", UUID.randomUUID().toString());
        String spanId = resolveHeader(httpReq, "X-Span-ID", UUID.randomUUID().toString().substring(0, 16));
        MDC.put(LogConstants.MDC_TRACE_ID, traceId);
        MDC.put(LogConstants.MDC_SPAN_ID, spanId);

        // userId from SecurityContext (if Spring Security is on the classpath)
        String userId = resolveUserId();
        MDC.put(LogConstants.MDC_USER_ID, userId);

        // Propagate request-id and trace headers back in the response
        httpResp.setHeader("X-Request-ID", requestId);
        httpResp.setHeader("X-Trace-ID", traceId);

        long start = System.currentTimeMillis();

        try {
            // Skip detailed logging for static resources and health checks
            if (!isStaticResource(httpReq.getRequestURI())) {
                log.info("-> {} {} [from: {}] [user: {}]",
                        httpReq.getMethod(),
                        httpReq.getRequestURI(),
                        resolveClientIp(httpReq),
                        userId);
            }

            chain.doFilter(request, response);

        } finally {
            long elapsed = System.currentTimeMillis() - start;

            if (!isStaticResource(httpReq.getRequestURI())) {
                log.info("<- {} {} -> {} ({}ms)",
                        httpReq.getMethod(),
                        httpReq.getRequestURI(),
                        httpResp.getStatus(),
                        elapsed);
            }

            // Clear MDC to prevent leaking into pooled threads
            MDC.clear();
        }
    }

    /**
     * Use incoming X-Request-ID header if present (e.g. from a gateway),
     * otherwise generate a new UUID.
     */
    private String resolveRequestId(HttpServletRequest req) {
        String header = req.getHeader("X-Request-ID");
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }

    /**
     * Resolve a header value, falling back to a default if absent.
     */
    private String resolveHeader(HttpServletRequest req, String headerName, String defaultValue) {
        String header = req.getHeader(headerName);
        return (header != null && !header.isBlank()) ? header : defaultValue;
    }

    /**
     * Resolve client IP, respecting X-Forwarded-For when behind a proxy.
     */
    private String resolveClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    /**
     * Extract the authenticated username from Spring Security's SecurityContext.
     * Returns "anonymous" if no authentication is present or user is not authenticated.
     */
    private String resolveUserId() {
        try {
            // Use reflection to avoid hard dependency on spring-security
            Class<?> contextHolderClass = Class.forName(
                    "org.springframework.security.core.context.SecurityContextHolder");
            Object context = contextHolderClass.getMethod("getContext").invoke(null);
            if (context == null) return "anonymous";

            Object authentication = context.getClass().getMethod("getAuthentication").invoke(context);
            if (authentication == null) return "anonymous";

            boolean isAuthenticated = (boolean) authentication.getClass()
                    .getMethod("isAuthenticated").invoke(authentication);
            if (!isAuthenticated) return "anonymous";

            Object name = authentication.getClass().getMethod("getName").invoke(authentication);
            String username = name != null ? name.toString() : "anonymous";

            // Filter out anonymous tokens
            if ("anonymousUser".equals(username)) return "anonymous";
            return username;
        } catch (Exception e) {
            // Spring Security not on classpath or other issue — that's fine
            return "anonymous";
        }
    }

    /**
     * Returns true for paths that are typically static resources
     * (CSS, JS, images, fonts, favicon, etc.).
     */
    private boolean isStaticResource(String uri) {
        return uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/images/")
                || uri.startsWith("/fonts/")
                || uri.startsWith("/favicon")
                || uri.startsWith("/webjars/")
                || uri.endsWith(".ico")
                || uri.endsWith(".map");
    }
}

