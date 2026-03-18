package com.app.footballprediction.controller;

import com.app.footballprediction.service.CacheStatisticsService;
import com.app.footballprediction.service.CacheWarmingService;
import com.app.footballprediction.service.FootballDataApiService;
import com.app.footballprediction.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for Caffeine cache management.
 */
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cache", description = "Caffeine cache monitoring, clearing, pattern invalidation, and warmup")
public class CacheManagementController {

    private final CacheManager cacheManager;
    private final CacheStatisticsService cacheStatisticsService;
    private final CacheWarmingService cacheWarmingService;
    private final FootballDataApiService footballDataApiService;
    private final NewsService newsService;

    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearAllCaches() {
        log.info("Cache clear requested via API...");

        int clearedCount = cacheStatisticsService.clearAllCaches();
        footballDataApiService.clearCache();
        newsService.clearCache();

        return ResponseEntity.ok(Map.of(
                "status", "All caches cleared successfully",
                "cachesCleared", clearedCount,
                "caches", cacheManager.getCacheNames()
        ));
    }

    @PostMapping("/clear/{cacheName}")
    public ResponseEntity<?> clearSpecificCache(@PathVariable String cacheName) {
        log.info("Clearing cache: {}", cacheName);

        boolean cleared = cacheStatisticsService.clearCache(cacheName);
        if (cleared) {
            return ResponseEntity.ok(Map.of("status", "Cache cleared successfully", "cache", cacheName));
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Cache not found", "cache", cacheName,
                "availableCaches", cacheManager.getCacheNames()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        Map<String, Object> stats = cacheStatisticsService.getAllCacheStatistics();
        stats.put("status", "active");
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/warmup")
    public ResponseEntity<?> getCacheWarmupStatus() {
        return ResponseEntity.ok(cacheStatisticsService.getCacheWarmupStatus());
    }

    @GetMapping("/stats/{cacheName}")
    public ResponseEntity<?> getCacheStats(@PathVariable String cacheName) {
        Map<String, Object> stats = cacheStatisticsService.getCacheStatistics(cacheName);
        if (stats != null) {
            return ResponseEntity.ok(stats);
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Cache not found", "cache", cacheName,
                "availableCaches", cacheManager.getCacheNames()
        ));
    }

    @PostMapping("/invalidate/{cacheName}")
    public ResponseEntity<Map<String, Object>> invalidateCacheByPattern(
            @PathVariable String cacheName,
            @RequestParam String pattern) {
        log.info("Invalidating entries matching '{}' from cache '{}'", pattern, cacheName);
        int count = cacheStatisticsService.invalidateByKeyPattern(cacheName, pattern);
        return ResponseEntity.ok(Map.of(
                "status", "success", "cache", cacheName,
                "pattern", pattern, "entriesInvalidated", count
        ));
    }

    @PostMapping("/warmup")
    public ResponseEntity<Map<String, Object>> triggerCacheWarmup() {
        log.info("Manual cache warmup triggered via API");
        cacheWarmingService.manualWarmUp();
        return ResponseEntity.ok(Map.of(
                "status", "Cache warmup initiated",
                "note", "Warmup runs asynchronously"
        ));
    }
}

