package com.app.footballprediction.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for monitoring and managing cache statistics.
 * Provides detailed insights into cache performance including hit rates,
 * eviction counts, and memory usage estimates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheStatisticsService {

    private final CacheManager cacheManager;

    /**
     * Get comprehensive statistics for all caches.
     */
    public Map<String, Object> getAllCacheStatistics() {
        Map<String, Object> allStats = new LinkedHashMap<>();

        Collection<String> cacheNames = cacheManager.getCacheNames();
        List<Map<String, Object>> cacheDetailsList = new ArrayList<>();

        long totalHits = 0;
        long totalMisses = 0;
        long totalEvictions = 0;
        long totalSize = 0;

        for (String cacheName : cacheNames) {
            Map<String, Object> cacheDetail = getCacheStatistics(cacheName);
            if (cacheDetail != null) {
                cacheDetailsList.add(cacheDetail);

                // Aggregate totals
                totalHits += (long) cacheDetail.getOrDefault("hitCount", 0L);
                totalMisses += (long) cacheDetail.getOrDefault("missCount", 0L);
                totalEvictions += (long) cacheDetail.getOrDefault("evictionCount", 0L);
                totalSize += (long) cacheDetail.getOrDefault("size", 0L);
            }
        }

        // Calculate overall hit rate
        double overallHitRate = (totalHits + totalMisses) > 0
            ? (double) totalHits / (totalHits + totalMisses) * 100
            : 0.0;

        allStats.put("caches", cacheDetailsList);
        allStats.put("summary", Map.of(
            "totalCaches", cacheNames.size(),
            "totalHits", totalHits,
            "totalMisses", totalMisses,
            "totalEvictions", totalEvictions,
            "totalEntries", totalSize,
            "overallHitRate", String.format("%.2f%%", overallHitRate)
        ));

        return allStats;
    }

    /**
     * Get statistics for a specific cache.
     */
    public Map<String, Object> getCacheStatistics(String cacheName) {
        org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);

        if (springCache == null) {
            return null;
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("name", cacheName);

        if (springCache instanceof CaffeineCache caffeineCache) {
            Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
            CacheStats cacheStats = nativeCache.stats();

            stats.put("size", nativeCache.estimatedSize());
            stats.put("hitCount", cacheStats.hitCount());
            stats.put("missCount", cacheStats.missCount());
            stats.put("hitRate", String.format("%.2f%%", cacheStats.hitRate() * 100));
            stats.put("missRate", String.format("%.2f%%", cacheStats.missRate() * 100));
            stats.put("evictionCount", cacheStats.evictionCount());
            stats.put("loadSuccessCount", cacheStats.loadSuccessCount());
            stats.put("loadFailureCount", cacheStats.loadFailureCount());
            stats.put("averageLoadPenalty", String.format("%.2f ms", cacheStats.averageLoadPenalty() / 1_000_000.0));
            stats.put("requestCount", cacheStats.requestCount());

            // Add cache keys (limited to first 20 for performance)
            List<String> keys = nativeCache.asMap().keySet().stream()
                .limit(20)
                .map(Object::toString)
                .collect(Collectors.toList());
            stats.put("sampleKeys", keys);
            stats.put("keysLimited", nativeCache.estimatedSize() > 20);
        } else {
            stats.put("type", springCache.getClass().getSimpleName());
            stats.put("nativeCache", springCache.getNativeCache().getClass().getSimpleName());
        }

        return stats;
    }

    /**
     * Clear a specific cache.
     */
    public boolean clearCache(String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("Cache '{}' cleared", cacheName);
            return true;
        }
        return false;
    }

    /**
     * Clear all caches.
     */
    public int clearAllCaches() {
        int count = 0;
        for (String cacheName : cacheManager.getCacheNames()) {
            if (clearCache(cacheName)) {
                count++;
            }
        }
        log.info("Cleared {} caches", count);
        return count;
    }

    /**
     * Invalidate specific cache entries by key pattern.
     */
    public int invalidateByKeyPattern(String cacheName, String keyPattern) {
        org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);

        if (springCache == null) {
            return 0;
        }

        if (springCache instanceof CaffeineCache caffeineCache) {
            Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();

            List<Object> keysToRemove = nativeCache.asMap().keySet().stream()
                .filter(key -> key.toString().contains(keyPattern))
                .toList();

            keysToRemove.forEach(nativeCache::invalidate);
            log.info("Invalidated {} entries matching pattern '{}' from cache '{}'",
                keysToRemove.size(), keyPattern, cacheName);

            return keysToRemove.size();
        }

        return 0;
    }

    /**
     * Get cache warmup status - useful for checking if caches are populated.
     */
    public Map<String, Object> getCacheWarmupStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        for (String cacheName : cacheManager.getCacheNames()) {
            org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);

            if (springCache instanceof CaffeineCache caffeineCache) {
                Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                long size = nativeCache.estimatedSize();
                CacheStats stats = nativeCache.stats();

                String warmupLevel;
                if (size == 0) {
                    warmupLevel = "COLD";
                } else if (stats.hitRate() > 0.8) {
                    warmupLevel = "HOT";
                } else if (stats.hitRate() > 0.5) {
                    warmupLevel = "WARM";
                } else {
                    warmupLevel = "WARMING";
                }

                status.put(cacheName, Map.of(
                    "entries", size,
                    "hitRate", String.format("%.2f%%", stats.hitRate() * 100),
                    "status", warmupLevel
                ));
            }
        }

        return status;
    }

    /**
     * Scheduled task to log cache statistics (runs every 5 minutes).
     */
    @Scheduled(fixedRate = 300000)
    public void logPeriodicStatistics() {
        if (!log.isInfoEnabled()) {
            return;
        }

        StringBuilder sb = new StringBuilder("\n=== Cache Statistics Report ===\n");

        long totalHits = 0;
        long totalMisses = 0;

        for (String cacheName : cacheManager.getCacheNames()) {
            org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);

            if (springCache instanceof CaffeineCache caffeineCache) {
                Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                CacheStats stats = nativeCache.stats();

                totalHits += stats.hitCount();
                totalMisses += stats.missCount();

                sb.append(String.format("  %-20s: size=%-5d hits=%-6d misses=%-6d hitRate=%.1f%%%n",
                    cacheName,
                    nativeCache.estimatedSize(),
                    stats.hitCount(),
                    stats.missCount(),
                    stats.hitRate() * 100));
            }
        }

        double overallHitRate = (totalHits + totalMisses) > 0
            ? (double) totalHits / (totalHits + totalMisses) * 100
            : 0.0;

        sb.append(String.format("  %-20s: hits=%-6d misses=%-6d hitRate=%.1f%%%n",
            "TOTAL",
            totalHits,
            totalMisses,
            overallHitRate));

        sb.append("================================");

        log.info(sb.toString());
    }
}

