package com.app.footballprediction.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Service for collecting and exposing ingestion metrics.
 * Provides visibility into pipeline usage and health.
 */
@Service
@Slf4j
public class IngestionMetricsService {

    // Counters
    private final AtomicLong totalIngestions = new AtomicLong(0);
    private final AtomicLong successfulIngestions = new AtomicLong(0);
    private final AtomicLong failedIngestions = new AtomicLong(0);
    private final AtomicLong totalMatchesProcessed = new AtomicLong(0);
    private final AtomicLong totalMatchesInserted = new AtomicLong(0);
    private final AtomicLong totalMatchesUpdated = new AtomicLong(0);

    // Pipeline usage tracking
    private final Map<String, AtomicLong> pipelineUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> providerUsage = new ConcurrentHashMap<>();

    // Timing metrics
    private final Map<String, AtomicLong> totalDurations = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> ingestionCounts = new ConcurrentHashMap<>();

    // Error tracking
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();

    // ══════════════════════════════════════════════════════════════
    // Recording Methods
    // ══════════════════════════════════════════════════════════════

    /**
     * Record a successful ingestion.
     */
    public void recordIngestionSuccess(String competition, long durationMs,
                                       int processed, int inserted, int updated) {
        totalIngestions.incrementAndGet();
        successfulIngestions.incrementAndGet();
        totalMatchesProcessed.addAndGet(processed);
        totalMatchesInserted.addAndGet(inserted);
        totalMatchesUpdated.addAndGet(updated);

        // Track duration for competition
        totalDurations.computeIfAbsent(competition, k -> new AtomicLong(0))
            .addAndGet(durationMs);
        ingestionCounts.computeIfAbsent(competition, k -> new AtomicLong(0))
            .incrementAndGet();

        log.info("📊 Ingestion SUCCESS: {} - {} processed, {} inserted, {} updated in {}ms",
            competition, processed, inserted, updated, durationMs);
    }

    /**
     * Record a failed ingestion.
     */
    public void recordIngestionFailure(String competition, Exception e) {
        totalIngestions.incrementAndGet();
        failedIngestions.incrementAndGet();

        String errorType = e.getClass().getSimpleName();
        errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0))
            .incrementAndGet();

        log.error("📊 Ingestion FAILED: {} - {}: {}", competition, errorType, e.getMessage());
    }

    /**
     * Record pipeline usage (LEGACY or NEW).
     */
    public void recordPipelineUsage(String pipeline) {
        pipelineUsage.computeIfAbsent(pipeline, k -> new AtomicLong(0))
            .incrementAndGet();
    }

    /**
     * Record provider usage.
     */
    public void recordProviderUsage(String provider) {
        providerUsage.computeIfAbsent(provider, k -> new AtomicLong(0))
            .incrementAndGet();
    }

    /**
     * Record shadow validation failure.
     */
    public void recordShadowValidationFailure(String competition) {
        errorCounts.computeIfAbsent("SHADOW_VALIDATION_FAILURE", k -> new AtomicLong(0))
            .incrementAndGet();
        log.warn("📊 Shadow validation FAILED for {}", competition);
    }

    /**
     * Record shadow validation success.
     */
    public void recordShadowValidationSuccess(String competition, double matchRate) {
        log.info("📊 Shadow validation PASSED for {} (match rate: {:.1f}%)",
            competition, matchRate * 100);
    }

    // ══════════════════════════════════════════════════════════════
    // Retrieval Methods
    // ══════════════════════════════════════════════════════════════

    /**
     * Get all current metrics.
     */
    public IngestionMetrics getMetrics() {
        return IngestionMetrics.builder()
            .totalIngestions(totalIngestions.get())
            .successfulIngestions(successfulIngestions.get())
            .failedIngestions(failedIngestions.get())
            .successRate(calculateSuccessRate())
            .totalMatchesProcessed(totalMatchesProcessed.get())
            .totalMatchesInserted(totalMatchesInserted.get())
            .totalMatchesUpdated(totalMatchesUpdated.get())
            .pipelineUsage(toMap(pipelineUsage))
            .providerUsage(toMap(providerUsage))
            .averageDurations(calculateAverageDurations())
            .errorCounts(toMap(errorCounts))
            .build();
    }

    /**
     * Get pipeline usage breakdown.
     */
    public Map<String, Long> getPipelineUsage() {
        return toMap(pipelineUsage);
    }

    /**
     * Get provider usage breakdown.
     */
    public Map<String, Long> getProviderUsage() {
        return toMap(providerUsage);
    }

    /**
     * Get success rate.
     */
    public double getSuccessRate() {
        return calculateSuccessRate();
    }

    // ══════════════════════════════════════════════════════════════
    // Reset Methods
    // ══════════════════════════════════════════════════════════════

    /**
     * Reset all metrics (for testing or admin reset).
     */
    public void reset() {
        totalIngestions.set(0);
        successfulIngestions.set(0);
        failedIngestions.set(0);
        totalMatchesProcessed.set(0);
        totalMatchesInserted.set(0);
        totalMatchesUpdated.set(0);
        pipelineUsage.clear();
        providerUsage.clear();
        totalDurations.clear();
        ingestionCounts.clear();
        errorCounts.clear();
        log.info("📊 Metrics reset");
    }

    // ══════════════════════════════════════════════════════════════
    // Helper Methods
    // ══════════════════════════════════════════════════════════════

    private double calculateSuccessRate() {
        long total = totalIngestions.get();
        if (total == 0) return 1.0;
        return (double) successfulIngestions.get() / total;
    }

    private Map<String, Long> calculateAverageDurations() {
        Map<String, Long> averages = new HashMap<>();
        for (String competition : totalDurations.keySet()) {
            long total = totalDurations.get(competition).get();
            long count = ingestionCounts.getOrDefault(competition, new AtomicLong(1)).get();
            averages.put(competition, count > 0 ? total / count : 0);
        }
        return averages;
    }

    private Map<String, Long> toMap(Map<String, AtomicLong> atomicMap) {
        return atomicMap.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().get()
            ));
    }

    /**
     * Metrics data class.
     */
    @lombok.Data
    @lombok.Builder
    public static class IngestionMetrics {
        private long totalIngestions;
        private long successfulIngestions;
        private long failedIngestions;
        private double successRate;
        private long totalMatchesProcessed;
        private long totalMatchesInserted;
        private long totalMatchesUpdated;
        private Map<String, Long> pipelineUsage;
        private Map<String, Long> providerUsage;
        private Map<String, Long> averageDurations;
        private Map<String, Long> errorCounts;
    }
}

