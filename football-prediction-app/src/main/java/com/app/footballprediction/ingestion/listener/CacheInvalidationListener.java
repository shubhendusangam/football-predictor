package com.app.footballprediction.ingestion.listener;

import com.app.common.ingestion.event.CacheInvalidationEvent;
import com.app.common.ingestion.event.MatchUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;

/**
 * Listens to ingestion events and invalidates relevant caches.
 *
 * <p><b>IMPORTANT:</b> This listener uses EXISTING cache names from CacheConfig.
 * It does NOT introduce any new cache names or change cache keys.
 *
 * <p>Cache invalidation happens AFTER successful database commit to ensure
 * consistency. If the transaction rolls back, caches remain valid.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationListener {

    private final CacheManager cacheManager;

    // Existing cache names from CacheConfig
    private static final String CACHE_TRENDING_INSIGHTS = "trendingInsights";
    private static final String CACHE_TEAM_STATS = "teamStats";
    private static final String CACHE_MATCHES = "matches";
    private static final String CACHE_PREDICTIONS = "predictions";
    private static final String CACHE_H2H_INSIGHTS = "h2hInsights";
    private static final String CACHE_SEASON_STATS = "seasonStats";
    private static final String CACHE_STANDINGS = "standings";
    private static final String CACHE_FORM_GUIDE = "formGuide";

    /**
     * Handle explicit cache invalidation event.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCacheInvalidation(CacheInvalidationEvent event) {
        log.debug("Received CacheInvalidationEvent: {} caches, type: {}",
            event.getCacheNames().size(), event.getType());

        if (event.isFullInvalidation()) {
            // Clear entire caches
            for (String cacheName : event.getCacheNames()) {
                clearCache(cacheName);
            }
        } else {
            // Selective invalidation by keys
            for (String cacheName : event.getCacheNames()) {
                for (String key : event.getCacheKeys()) {
                    evictFromCache(cacheName, key);
                }
            }
        }
    }

    /**
     * Handle match updated event - invalidate related caches.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchUpdated(MatchUpdatedEvent event) {
        String season = event.getMatch().getSeason();
        String homeTeam = event.getMatch().getHomeTeam();
        String awayTeam = event.getMatch().getAwayTeam();

        log.debug("Invalidating caches for match update: {} vs {} ({})",
            homeTeam, awayTeam, season);

        // Invalidate trending insights for this season
        evictFromCache(CACHE_TRENDING_INSIGHTS, season);
        evictFromCache(CACHE_TRENDING_INSIGHTS, "all");

        // Invalidate team stats for both teams
        evictFromCache(CACHE_TEAM_STATS, homeTeam);
        evictFromCache(CACHE_TEAM_STATS, awayTeam);

        // Invalidate H2H insights for this pair
        String h2hKey = homeTeam + "_" + awayTeam;
        evictFromCache(CACHE_H2H_INSIGHTS, h2hKey);
        evictFromCache(CACHE_H2H_INSIGHTS, awayTeam + "_" + homeTeam);

        // Invalidate season stats
        evictFromCache(CACHE_SEASON_STATS, season);

        // Invalidate predictions that might be affected
        evictFromCache(CACHE_PREDICTIONS, homeTeam);
        evictFromCache(CACHE_PREDICTIONS, awayTeam);

        // Invalidate form guide cache — keys are compound (teamName-numMatches)
        // so a full clear is needed to ensure both teams' form data refreshes
        clearCache(CACHE_FORM_GUIDE);

        log.debug("Cache invalidation complete for match: {} vs {}", homeTeam, awayTeam);
    }

    /**
     * Clear an entire cache.
     */
    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.debug("Cache cleared: {}", cacheName);
        } else {
            log.warn("Cache not found: {}", cacheName);
        }
    }

    /**
     * Evict a specific key from a cache.
     */
    private void evictFromCache(String cacheName, String key) {
        if (key == null) return;

        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            log.trace("Evicted from cache '{}': {}", cacheName, key);
        }
    }

    /**
     * Invalidate all match-related caches.
     * Used for bulk updates.
     */
    public void invalidateAllMatchCaches() {
        Set<String> matchCaches = Set.of(
            CACHE_TRENDING_INSIGHTS,
            CACHE_TEAM_STATS,
            CACHE_MATCHES,
            CACHE_PREDICTIONS,
            CACHE_H2H_INSIGHTS,
            CACHE_SEASON_STATS,
            CACHE_FORM_GUIDE
        );

        for (String cacheName : matchCaches) {
            clearCache(cacheName);
        }

        log.info("All match-related caches invalidated");
    }

    /**
     * Invalidate caches for a specific season.
     */
    public void invalidateSeasonCaches(String season) {
        evictFromCache(CACHE_TRENDING_INSIGHTS, season);
        evictFromCache(CACHE_TRENDING_INSIGHTS, "all");
        evictFromCache(CACHE_SEASON_STATS, season);
        clearCache(CACHE_STANDINGS);
        clearCache(CACHE_FORM_GUIDE);

        log.info("Caches invalidated for season: {}", season);
    }
}

