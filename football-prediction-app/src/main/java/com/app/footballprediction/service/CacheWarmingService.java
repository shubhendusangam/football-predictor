package com.app.footballprediction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmUpCaches() {
        if (!warmupEnabled) {
            log.info("Cache warmup is disabled");
            return;
        }

        log.info("╔══════════════════════════════════════════╗");
        log.info("║  🔥 Starting Cache Warmup Process...     ║");
        log.info("╚══════════════════════════════════════════╝");

        long startTime = System.currentTimeMillis();
        int successCount = 0;

        try {
            // Warm up standings cache
            if (warmUpStandings()) successCount++;

            // Warm up scheduled matches cache
            if (warmUpScheduledMatches()) successCount++;

            // Warm up trending insights
            if (warmUpTrendingInsights()) successCount++;

            long duration = System.currentTimeMillis() - startTime;

            log.info("╔══════════════════════════════════════════╗");
            log.info("║  ✅ Cache Warmup Complete!               ║");
            log.info("║  → Warmed {}/{} caches in {}ms           ║", successCount, 3, duration);
            log.info("║  → Cache hit rate should improve!        ║");
            log.info("╚══════════════════════════════════════════╝");

        } catch (Exception e) {
            log.warn("Cache warmup partially failed: {}", e.getMessage());
        }
    }

    private boolean warmUpStandings() {
        try {
            log.info("  → Warming up STANDINGS cache...");
            footballDataApiService.getStandings(defaultCompetition);
            log.info("  ✅ STANDINGS cache warmed");
            return true;
        } catch (Exception e) {
            log.warn("  ⚠️  Failed to warm STANDINGS cache: {}", e.getMessage());
            return false;
        }
    }

    private boolean warmUpScheduledMatches() {
        try {
            log.info("  → Warming up MATCHES cache...");
            footballDataApiService.getScheduledMatches(defaultCompetition);
            log.info("  ✅ MATCHES cache warmed");
            return true;
        } catch (Exception e) {
            log.warn("  ⚠️  Failed to warm MATCHES cache: {}", e.getMessage());
            return false;
        }
    }

    private boolean warmUpTrendingInsights() {
        try {
            log.info("  → Warming up TRENDING INSIGHTS cache...");
            trendingInsightsService.getTrendingInsights();
            log.info("  ✅ TRENDING INSIGHTS cache warmed");
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

