package com.app.footballprediction.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter filter using simple Token Bucket algorithm.
 * Limits requests per IP address to prevent API abuse.
 * No external dependencies required.
 */
@Component
@Order(1)
@Slf4j
public class RateLimitFilter implements Filter {

    @Value("${ratelimit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${ratelimit.enabled:true}")
    private boolean rateLimitEnabled;

    // Store buckets per IP address
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!rateLimitEnabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip rate limiting for static resources
        String path = httpRequest.getRequestURI();
        if (isStaticResource(path)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);
        TokenBucket bucket = buckets.computeIfAbsent(clientIp,
                k -> new TokenBucket(requestsPerMinute, requestsPerMinute));

        if (bucket.tryConsume()) {
            // Add rate limit headers
            httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
            chain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("""
                {
                    "error": "Too many requests",
                    "message": "Rate limit exceeded. Please wait before making more requests.",
                    "retryAfter": 60
                }
                """);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isStaticResource(String path) {
        return path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.endsWith(".ico") ||
               path.endsWith(".html") ||
               path.equals("/");
    }

    /**
     * Simple Token Bucket implementation for rate limiting.
     * Refills tokens at a constant rate.
     */
    private static class TokenBucket {
        private final int maxTokens;
        private final int refillRate; // tokens per minute
        private final AtomicLong tokens;
        private volatile long lastRefillTime;

        public TokenBucket(int maxTokens, int refillRate) {
            this.maxTokens = maxTokens;
            this.refillRate = refillRate;
            this.tokens = new AtomicLong(maxTokens);
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refill();
            long currentTokens = tokens.get();
            if (currentTokens > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        public long getAvailableTokens() {
            refill();
            return tokens.get();
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long timePassed = now - lastRefillTime;

            // Refill tokens based on time passed (refillRate tokens per minute)
            long tokensToAdd = (timePassed * refillRate) / 60000; // 60000ms = 1 minute

            if (tokensToAdd > 0) {
                long newTokens = Math.min(maxTokens, tokens.get() + tokensToAdd);
                tokens.set(newTokens);
                lastRefillTime = now;
            }
        }
    }
}

