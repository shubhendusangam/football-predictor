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

        // Propagate request-id back in the response header for tracing
        httpResp.setHeader("X-Request-ID", requestId);

        long start = System.currentTimeMillis();

        try {
            // Skip detailed logging for static resources and health checks
            if (!isStaticResource(httpReq.getRequestURI())) {
                log.info("-> {} {} [from: {}]",
                        httpReq.getMethod(),
                        httpReq.getRequestURI(),
                        resolveClientIp(httpReq));
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

