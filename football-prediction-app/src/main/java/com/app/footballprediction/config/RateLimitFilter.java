package com.app.footballprediction.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter filter using a Token Bucket algorithm with tiered limits.
 *
 * <p>Features:
 * <ul>
 *   <li>Per-IP rate limiting to prevent API abuse</li>
 *   <li>Tiered limits: lower limits for expensive endpoints (predictions, training)</li>
 *   <li>Standard rate limit headers (X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After)</li>
 *   <li>Static resources excluded from rate limiting</li>
 *   <li>Periodic stale bucket cleanup to prevent memory leaks</li>
 *   <li>Configurable via application.properties</li>
 * </ul>
 */
@Component
@Order(1)
@Slf4j
public class RateLimitFilter implements Filter {

    @Value("${ratelimit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${ratelimit.requests-per-minute:120}")
    private int defaultRequestsPerMinute;

    @Value("${ratelimit.prediction.requests-per-minute:30}")
    private int predictionRequestsPerMinute;

    @Value("${ratelimit.admin.requests-per-minute:20}")
    private int adminRequestsPerMinute;

    @Value("${ratelimit.burst-capacity-multiplier:1.5}")
    private double burstCapacityMultiplier;

    // Store buckets per IP+tier combination
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    // Track blocked IPs for monitoring
    private final Map<String, AtomicLong> blockedCounts = new ConcurrentHashMap<>();

    private static final String TIER_DEFAULT = "default";
    private static final String TIER_PREDICTION = "prediction";
    private static final String TIER_ADMIN = "admin";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!rateLimitEnabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Skip rate limiting for static resources and health checks
        if (isExemptFromRateLimiting(path)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);
        String tier = classifyRequestTier(path);
        int tierLimit = getTierLimit(tier);
        String bucketKey = clientIp + ":" + tier;

        TokenBucket bucket = buckets.computeIfAbsent(bucketKey,
                k -> new TokenBucket((int) (tierLimit * burstCapacityMultiplier), tierLimit));

        // Always add rate limit headers
        httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(tierLimit));
        httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, bucket.getAvailableTokens())));

        if (bucket.tryConsume()) {
            chain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            blockedCounts.computeIfAbsent(clientIp, k -> new AtomicLong()).incrementAndGet();
            log.warn("Rate limit exceeded for IP: {} on path: {} (tier: {}, limit: {}/min)",
                    clientIp, path, tier, tierLimit);

            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.setHeader("Retry-After", "60");
            httpResponse.getWriter().write("""
                {
                    "error": "Too Many Requests",
                    "message": "Rate limit exceeded. Please wait before making more requests.",
                    "retryAfterSeconds": 60,
                    "limit": %d
                }
                """.formatted(tierLimit));
        }
    }

    /**
     * Classify the request into a rate limiting tier based on the URL path.
     */
    private String classifyRequestTier(String path) {
        if (path.startsWith("/api/predict") || path.startsWith("/api/predictions")) {
            return TIER_PREDICTION;
        }
        if (path.startsWith("/api/admin") || path.startsWith("/api/model/train") ||
                path.startsWith("/api/data/")) {
            return TIER_ADMIN;
        }
        return TIER_DEFAULT;
    }

    /**
     * Get the rate limit for a specific tier.
     */
    private int getTierLimit(String tier) {
        return switch (tier) {
            case TIER_PREDICTION -> predictionRequestsPerMinute;
            case TIER_ADMIN -> adminRequestsPerMinute;
            default -> defaultRequestsPerMinute;
        };
    }

    private String getClientIp(HttpServletRequest request) {
        // Check standard proxy headers
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isExemptFromRateLimiting(String path) {
        return path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/assets/") ||
               path.endsWith(".ico") ||
               path.endsWith(".html") ||
               path.endsWith(".png") ||
               path.endsWith(".jpg") ||
               path.endsWith(".svg") ||
               path.startsWith("/actuator/health") ||
               path.equals("/");
    }

    /**
     * Periodically clean up stale token buckets to prevent memory leaks.
     * Runs every 10 minutes. Removes buckets that haven't been used recently
     * (i.e., they have refilled to max tokens).
     */
    @Scheduled(fixedRate = 600_000) // Every 10 minutes
    public void cleanupStaleBuckets() {
        int before = buckets.size();
        buckets.entrySet().removeIf(entry -> entry.getValue().isStale());
        int removed = before - buckets.size();
        if (removed > 0) {
            log.debug("Cleaned up {} stale rate limit buckets ({} remaining)", removed, buckets.size());
        }
    }

    /**
     * Get rate limiting statistics for monitoring.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("enabled", rateLimitEnabled);
        stats.put("activeBuckets", buckets.size());
        stats.put("defaultLimitPerMinute", defaultRequestsPerMinute);
        stats.put("predictionLimitPerMinute", predictionRequestsPerMinute);
        stats.put("adminLimitPerMinute", adminRequestsPerMinute);

        // Top blocked IPs
        Map<String, Long> topBlocked = new java.util.LinkedHashMap<>();
        blockedCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(10)
                .forEach(e -> topBlocked.put(e.getKey(), e.getValue().get()));
        stats.put("topBlockedIPs", topBlocked);

        return stats;
    }

    /**
     * Simple Token Bucket implementation for rate limiting.
     * Refills tokens at a constant rate (tokens per minute).
     */
    private static class TokenBucket {
        private final int maxTokens;
        private final int refillRate; // tokens per minute
        private final AtomicLong tokens;
        private volatile long lastRefillTime;
        private volatile long lastAccessTime;

        public TokenBucket(int maxTokens, int refillRate) {
            this.maxTokens = maxTokens;
            this.refillRate = refillRate;
            this.tokens = new AtomicLong(maxTokens);
            this.lastRefillTime = System.currentTimeMillis();
            this.lastAccessTime = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refill();
            lastAccessTime = System.currentTimeMillis();
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

        /**
         * A bucket is stale if it hasn't been accessed in 15 minutes
         * and has fully refilled.
         */
        public boolean isStale() {
            long idleMs = System.currentTimeMillis() - lastAccessTime;
            return idleMs > 900_000 && tokens.get() >= maxTokens; // 15 min idle + full
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long timePassed = now - lastRefillTime;
            long tokensToAdd = (timePassed * refillRate) / 60_000;

            if (tokensToAdd > 0) {
                long newTokens = Math.min(maxTokens, tokens.get() + tokensToAdd);
                tokens.set(newTokens);
                lastRefillTime = now;
            }
        }
    }
}

