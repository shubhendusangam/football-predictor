package com.app.footballprediction.service;

import com.app.footballprediction.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Centralizes cache invalidation logic that was previously scattered
 * across {@code ApiDataSyncService}, {@code PredictionController}, and
 * individual services.
 *
 * <p>Call {@link #clearAllDataCaches()} after any data sync or ingestion
 * operation to ensure the UI displays fresh data.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheOrchestrationService {

    private final CacheManager cacheManager;

    /** All caches that depend on match or standings data. */
    private static final String[] DATA_CACHES = {
            CacheConfig.CACHE_STANDINGS,
            CacheConfig.CACHE_MATCHES,
            CacheConfig.CACHE_TEAM_STATS,
            CacheConfig.CACHE_TEAM_FORM,
            CacheConfig.CACHE_H2H_INSIGHTS,
            CacheConfig.CACHE_TRENDING_INSIGHTS,
            CacheConfig.CACHE_PREDICTIONS,
            CacheConfig.CACHE_TEAM_ANALYTICS,
            CacheConfig.CACHE_PRE_MATCH_INSIGHTS,
            CacheConfig.CACHE_LEAGUE_STATS,
            CacheConfig.CACHE_ELO_RATINGS,
            CacheConfig.CACHE_API_RESPONSES,
            CacheConfig.CACHE_SEASONS,
            CacheConfig.CACHE_SEASON_STATS,
            CacheConfig.CACHE_API_SYNC
    };

    /**
     * Clear all caches that depend on match/standings data.
     * Safe to call frequently — no-ops for caches that don't exist.
     *
     * @return number of caches successfully cleared
     */
    public int clearAllDataCaches() {
        int cleared = 0;
        for (String cacheName : DATA_CACHES) {
            if (clearSingle(cacheName)) {
                cleared++;
            }
        }
        log.debug("Cleared {} data caches", cleared);
        return cleared;
    }

    /**
     * Clear a single cache by name.
     *
     * @return true if the cache existed and was cleared
     */
    public boolean clearSingle(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.debug("Cleared cache: {}", cacheName);
                return true;
            }
        } catch (Exception e) {
            log.warn("Failed to clear cache {}: {}", cacheName, e.getMessage());
        }
        return false;
    }
}

