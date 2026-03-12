package com.app.footballprediction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for warming up caches at application startup.
 * Pre-populates frequently accessed data to improve initial response times.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheWarmingService {

    private final FootballDataApiService footballDataApiService;
    private final TrendingInsightsService trendingInsightsService;

    @Value("${cache.warmup.enabled:true}")
    private boolean warmupEnabled;

    @Value("${football.api.competition:PL}")
    private String defaultCompetition;

    /**
     * Warm up caches after application is ready.
     * Runs asynchronously to not block startup.
     * Standings and scheduled matches are warmed in parallel (independent API calls).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmUpCaches() {
        if (!warmupEnabled) {
            log.info("Cache warmup is disabled");
            return;
        }

        log.debug("╔══════════════════════════════════════════╗");
        log.debug("║  🔥 Starting Cache Warmup Process...     ║");
        log.debug("╚══════════════════════════════════════════╝");

        long startTime = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);

        try {
            // Warm up standings and scheduled matches in parallel (independent API calls)
            CompletableFuture<Boolean> standingsFuture = CompletableFuture.supplyAsync(this::warmUpStandings);
            CompletableFuture<Boolean> matchesFuture = CompletableFuture.supplyAsync(this::warmUpScheduledMatches);

            // Wait for both API calls to complete
            CompletableFuture.allOf(standingsFuture, matchesFuture).join();

            if (Boolean.TRUE.equals(standingsFuture.get())) successCount.incrementAndGet();
            if (Boolean.TRUE.equals(matchesFuture.get())) successCount.incrementAndGet();

            // Warm up trending insights (can run after or in parallel, no dependency)
            if (warmUpTrendingInsights()) successCount.incrementAndGet();

            long duration = System.currentTimeMillis() - startTime;

            log.info("✅ Cache warmup complete: warmed {}/{} caches in {}ms", successCount.get(), 3, duration);

        } catch (Exception e) {
            log.warn("Cache warmup partially failed: {}", e.getMessage());
        }
    }

    private boolean warmUpStandings() {
        try {
            log.debug("  → Warming up STANDINGS cache...");
            footballDataApiService.getStandings(defaultCompetition);
            log.debug("  ✅ STANDINGS cache warmed");
            return true;
        } catch (Exception e) {
            log.warn("  ⚠️  Failed to warm STANDINGS cache: {}", e.getMessage());
            return false;
        }
    }

    private boolean warmUpScheduledMatches() {
        try {
            log.debug("  → Warming up MATCHES cache...");
            footballDataApiService.getScheduledMatches(defaultCompetition);
            log.debug("  ✅ MATCHES cache warmed");
            return true;
        } catch (Exception e) {
            log.warn("  ⚠️  Failed to warm MATCHES cache: {}", e.getMessage());
            return false;
        }
    }

    private boolean warmUpTrendingInsights() {
        try {
            log.debug("  → Warming up TRENDING INSIGHTS cache...");
            trendingInsightsService.getTrendingInsights();
            log.debug("  ✅ TRENDING INSIGHTS cache warmed");
            return true;
        } catch (Exception e) {
            log.warn("  ⚠️  Failed to warm TRENDING INSIGHTS cache: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Manually trigger cache warmup.
     * Useful for refreshing caches after clearing.
     */
    public void manualWarmUp() {
        log.info("Manual cache warmup triggered");
        warmUpCaches();
    }
}

