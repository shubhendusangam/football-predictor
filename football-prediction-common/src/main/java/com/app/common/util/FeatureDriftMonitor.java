package com.app.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Feature drift detection and data quality monitoring.
 *
 * <p>Tracks feature distribution statistics during training and alerts when
 * prediction-time features deviate significantly from training-time distributions.
 * This catches silent data pipeline failures (e.g., shot data stops arriving →
 * homeShotsOnTargetAvg silently falls to 0.0).</p>
 *
 * <p>Usage:
 * <ol>
 *   <li>During training: call {@link #recordTrainingStats(String, double)} for each feature value</li>
 *   <li>After training: call {@link #finalizeTrainingStats()} to compute baselines</li>
 *   <li>During prediction: call {@link #checkFeatureDrift(String, double)} to detect anomalies</li>
 * </ol>
 */
@Slf4j
public class FeatureDriftMonitor {

    /** Threshold for z-score deviation to flag as drift (default: 3 standard deviations) */
    private static final double DRIFT_Z_THRESHOLD = 3.0;

    /** Threshold for null/zero rate increase to flag as data quality issue */
    private static final double NULL_RATE_ALERT_THRESHOLD = 0.5;

    // Training-time statistics per feature
    private final Map<String, FeatureStats> trainingStats = new ConcurrentHashMap<>();

    // Prediction-time running stats per feature
    private final Map<String, RunningStats> predictionStats = new ConcurrentHashMap<>();

    // Thread-safe null/zero rate tracking using atomic counters
    private final Map<String, NullZeroCounter> nullZeroCounts = new ConcurrentHashMap<>();

    /** Thread-safe counter pair for null/zero rate tracking. */
    private static class NullZeroCounter {
        final LongAdder nullCount = new LongAdder();
        final LongAdder totalCount = new LongAdder();
    }

    /**
     * Record a feature value during training for baseline statistics.
     */
    public void recordTrainingStats(String featureName, double value) {
        trainingStats.computeIfAbsent(featureName, k -> new FeatureStats()).add(value);
    }

    /**
     * Finalize training stats — compute mean and std deviation baselines.
     */
    public void finalizeTrainingStats() {
        trainingStats.forEach((name, stats) -> {
            stats.computeBaseline();
            log.debug("Training stats for {}: mean={}, std={}, min={}, max={}, nullRate={}",
                    name, stats.mean, stats.std, stats.min, stats.max, stats.nullRate);
        });
        log.info("Feature drift monitor initialized with {} features", trainingStats.size());
    }

    /**
     * Check if a feature value deviates from training-time distribution.
     *
     * @param featureName Name of the feature
     * @param value Current prediction-time value
     * @return DriftResult with drift status and details
     */
    public DriftResult checkFeatureDrift(String featureName, double value) {
        FeatureStats baseline = trainingStats.get(featureName);
        if (baseline == null) {
            return new DriftResult(featureName, false, "No baseline available");
        }

        // Track null/zero rates (thread-safe)
        NullZeroCounter counter = nullZeroCounts.computeIfAbsent(featureName, k -> new NullZeroCounter());
        counter.totalCount.increment();
        if (value == 0.0 || Double.isNaN(value)) {
            counter.nullCount.increment();
        }

        // Check z-score deviation
        if (baseline.std > 0) {
            double zScore = Math.abs(value - baseline.mean) / baseline.std;
            if (zScore > DRIFT_Z_THRESHOLD) {
                String msg = String.format("z-score=%.2f (threshold=%.1f), value=%.4f, training mean=%.4f, std=%.4f",
                        zScore, DRIFT_Z_THRESHOLD, value, baseline.mean, baseline.std);
                log.warn("⚠️ Feature drift detected for '{}': {}", featureName, msg);
                return new DriftResult(featureName, true, msg);
            }
        }

        // Check null/zero rate increase
        long total = counter.totalCount.sum();
        long nulls = counter.nullCount.sum();
        if (total > 10) {
            double currentNullRate = (double) nulls / total;
            if (currentNullRate > baseline.nullRate + NULL_RATE_ALERT_THRESHOLD) {
                String msg = String.format("Null/zero rate increased: training=%.2f, current=%.2f",
                        baseline.nullRate, currentNullRate);
                log.warn("⚠️ Data quality issue for '{}': {}", featureName, msg);
                return new DriftResult(featureName, true, msg);
            }
        }

        return new DriftResult(featureName, false, "OK");
    }

    /**
     * Get a summary of all features with their drift status.
     */
    public Map<String, String> getDriftSummary() {
        Map<String, String> summary = new HashMap<>();
        trainingStats.forEach((name, stats) -> {
            NullZeroCounter counter = nullZeroCounts.get(name);
            long total = counter != null ? counter.totalCount.sum() : 0;
            long nulls = counter != null ? counter.nullCount.sum() : 0;
            double currentNullRate = total > 0 ? (double) nulls / total : 0;
            summary.put(name, String.format("mean=%.4f, std=%.4f, trainingNullRate=%.2f, currentNullRate=%.2f",
                    stats.mean, stats.std, stats.nullRate, currentNullRate));
        });
        return summary;
    }

    /**
     * Reset prediction-time stats (call at the start of each evaluation period).
     */
    public void resetPredictionStats() {
        predictionStats.clear();
        nullZeroCounts.clear();
    }

    // ── Inner classes ──

    /**
     * Training-time feature statistics.
     */
    static class FeatureStats {
        double sum = 0;
        double sumSq = 0;
        int count = 0;
        int nullZeroCount = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        // Finalized values
        double mean = 0;
        double std = 0;
        double nullRate = 0;

        void add(double value) {
            count++;
            if (value == 0.0 || Double.isNaN(value)) {
                nullZeroCount++;
            }
            if (!Double.isNaN(value)) {
                sum += value;
                sumSq += value * value;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }

        void computeBaseline() {
            if (count > 0) {
                mean = sum / count;
                double variance = (sumSq / count) - (mean * mean);
                std = Math.sqrt(Math.max(0, variance));
                nullRate = (double) nullZeroCount / count;
            }
        }
    }

    /**
     * Running statistics for prediction-time tracking.
     */
    static class RunningStats {
        double sum = 0;
        int count = 0;

        void add(double value) {
            sum += value;
            count++;
        }

        double mean() {
            return count > 0 ? sum / count : 0;
        }
    }

    /**
     * Result of a feature drift check.
     */
    public record DriftResult(String featureName, boolean driftDetected, String message) {
    }
}

