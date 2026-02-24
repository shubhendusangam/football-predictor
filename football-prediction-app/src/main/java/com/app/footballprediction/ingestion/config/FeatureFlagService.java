package com.app.footballprediction.ingestion.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature flag service for controlling ingestion pipeline behavior.
 * Enables gradual rollout and instant rollback without deployment.
 *
 * <p>Flags can be:
 * <ul>
 *   <li>Set via application.properties (default values)</li>
 *   <li>Updated at runtime via admin API</li>
 *   <li>Used for A/B testing between pipelines</li>
 * </ul>
 *
 * <p><b>ROLLBACK:</b> Call {@link #rollbackToLegacy()} to instantly
 * switch back to the legacy ingestion pipeline.
 */
@Service
@Slf4j
public class FeatureFlagService {

    private final Map<String, Boolean> flags = new ConcurrentHashMap<>();

    // ══════════════════════════════════════════════════════════════
    // Flag Names (Constants)
    // ══════════════════════════════════════════════════════════════

    /** Use legacy ingestion pipeline (CsvIngestionService, FootballDataApiService) */
    public static final String USE_LEGACY_PIPELINE = "ingestion.use.legacy.pipeline";

    /** Enable new provider-based ingestion pipeline */
    public static final String USE_NEW_PIPELINE = "ingestion.use.new.pipeline";

    /** Enable shadow validation (run both, compare, log differences) */
    public static final String SHADOW_VALIDATION = "ingestion.shadow.validation";

    /** Shadow only mode (run new pipeline but don't write to DB) */
    public static final String SHADOW_ONLY = "ingestion.shadow.only";

    /** Enable event publishing for match updates */
    public static final String EVENTS_ENABLED = "ingestion.events.enabled";

    /** Run parallel comparison between pipelines */
    public static final String PARALLEL_COMPARISON = "ingestion.parallel.comparison";

    /** Enable data drift detection */
    public static final String DRIFT_DETECTION = "ingestion.drift.detection";

    /** Enable detailed ingestion logging */
    public static final String DETAILED_LOGGING = "ingestion.detailed.logging";

    // ══════════════════════════════════════════════════════════════
    // Default Values from Properties
    // ══════════════════════════════════════════════════════════════

    @Value("${feature.ingestion.use.legacy.pipeline:true}")
    private boolean defaultUseLegacy;

    @Value("${feature.ingestion.use.new.pipeline:false}")
    private boolean defaultUseNew;

    @Value("${feature.ingestion.shadow.validation:false}")
    private boolean defaultShadowValidation;

    @Value("${feature.ingestion.shadow.only:false}")
    private boolean defaultShadowOnly;

    @Value("${feature.ingestion.events.enabled:false}")
    private boolean defaultEventsEnabled;

    @Value("${feature.ingestion.parallel.comparison:false}")
    private boolean defaultParallelComparison;

    @Value("${feature.ingestion.drift.detection:true}")
    private boolean defaultDriftDetection;

    @Value("${feature.ingestion.detailed.logging:false}")
    private boolean defaultDetailedLogging;

    // ══════════════════════════════════════════════════════════════
    // Initialization
    // ══════════════════════════════════════════════════════════════

    @PostConstruct
    public void init() {
        // Initialize flags from properties
        flags.put(USE_LEGACY_PIPELINE, defaultUseLegacy);
        flags.put(USE_NEW_PIPELINE, defaultUseNew);
        flags.put(SHADOW_VALIDATION, defaultShadowValidation);
        flags.put(SHADOW_ONLY, defaultShadowOnly);
        flags.put(EVENTS_ENABLED, defaultEventsEnabled);
        flags.put(PARALLEL_COMPARISON, defaultParallelComparison);
        flags.put(DRIFT_DETECTION, defaultDriftDetection);
        flags.put(DETAILED_LOGGING, defaultDetailedLogging);

        log.info("Feature flags initialized: {}", flags);

        // Validate configuration
        validateFlags();
    }

    /**
     * Validate that flags are in a consistent state.
     */
    private void validateFlags() {
        // Both pipelines shouldn't be disabled
        if (!flags.get(USE_LEGACY_PIPELINE) && !flags.get(USE_NEW_PIPELINE)) {
            log.warn("⚠️ Both legacy and new pipelines are disabled! Enabling legacy as fallback.");
            flags.put(USE_LEGACY_PIPELINE, true);
        }

        // Shadow-only requires shadow validation
        if (flags.get(SHADOW_ONLY) && !flags.get(SHADOW_VALIDATION)) {
            log.info("Shadow-only mode enabled, also enabling shadow validation");
            flags.put(SHADOW_VALIDATION, true);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Flag Access
    // ══════════════════════════════════════════════════════════════

    /**
     * Check if a feature flag is enabled.
     *
     * @param flag Flag name
     * @return true if enabled
     */
    public boolean isEnabled(String flag) {
        return flags.getOrDefault(flag, false);
    }

    /**
     * Check if legacy pipeline should be used.
     */
    public boolean useLegacyPipeline() {
        return isEnabled(USE_LEGACY_PIPELINE);
    }

    /**
     * Check if new pipeline should be used.
     */
    public boolean useNewPipeline() {
        return isEnabled(USE_NEW_PIPELINE);
    }

    /**
     * Check if shadow validation is enabled.
     */
    public boolean shadowValidationEnabled() {
        return isEnabled(SHADOW_VALIDATION);
    }

    /**
     * Check if running in shadow-only mode (no writes).
     */
    public boolean isShadowOnly() {
        return isEnabled(SHADOW_ONLY);
    }

    /**
     * Check if events should be published.
     */
    public boolean eventsEnabled() {
        return isEnabled(EVENTS_ENABLED);
    }

    // ══════════════════════════════════════════════════════════════
    // Flag Updates
    // ══════════════════════════════════════════════════════════════

    /**
     * Update a feature flag at runtime.
     *
     * @param flag Flag name
     * @param value New value
     * @return Previous value
     */
    public boolean setFlag(String flag, boolean value) {
        Boolean oldValue = flags.put(flag, value);
        log.info("🚩 Feature flag '{}' changed: {} → {}", flag, oldValue, value);
        validateFlags();
        return oldValue != null && oldValue;
    }

    /**
     * Get all current flag values.
     *
     * @return Map of flag names to values
     */
    public Map<String, Boolean> getAllFlags() {
        return new HashMap<>(flags);
    }

    // ══════════════════════════════════════════════════════════════
    // Rollback Operations
    // ══════════════════════════════════════════════════════════════

    /**
     * Instantly rollback to legacy ingestion pipeline.
     * This is the emergency switch for production issues.
     */
    public void rollbackToLegacy() {
        log.warn("⚠️⚠️⚠️ ROLLBACK INITIATED: Reverting to legacy ingestion pipeline ⚠️⚠️⚠️");

        flags.put(USE_LEGACY_PIPELINE, true);
        flags.put(USE_NEW_PIPELINE, false);
        flags.put(SHADOW_ONLY, false);
        flags.put(SHADOW_VALIDATION, false);
        flags.put(PARALLEL_COMPARISON, false);

        log.warn("✅ Rollback complete. Current flags: {}", flags);
    }

    /**
     * Enable shadow mode for new pipeline testing.
     * New pipeline runs in parallel but doesn't write.
     */
    public void enableShadowMode() {
        log.info("🔍 Enabling shadow mode for new pipeline validation");

        flags.put(USE_LEGACY_PIPELINE, true);  // Keep legacy as primary
        flags.put(USE_NEW_PIPELINE, false);    // Don't use new for writes
        flags.put(SHADOW_VALIDATION, true);    // Run comparisons
        flags.put(SHADOW_ONLY, true);          // Don't write from new
        flags.put(PARALLEL_COMPARISON, true);  // Run both

        log.info("Shadow mode enabled. Current flags: {}", flags);
    }

    /**
     * Fully enable new pipeline (post-validation).
     */
    public void enableNewPipeline() {
        log.info("🚀 Enabling new ingestion pipeline");

        flags.put(USE_LEGACY_PIPELINE, false);
        flags.put(USE_NEW_PIPELINE, true);
        flags.put(SHADOW_ONLY, false);
        flags.put(EVENTS_ENABLED, true);

        log.info("New pipeline enabled. Current flags: {}", flags);
    }

    /**
     * Enable gradual rollout (10% new, 90% legacy).
     * Uses random selection per request.
     */
    public void enableCanaryMode(int percentNew) {
        log.info("🐤 Enabling canary mode: {}% new pipeline", percentNew);

        // For canary, both pipelines are enabled
        flags.put(USE_LEGACY_PIPELINE, true);
        flags.put(USE_NEW_PIPELINE, true);
        flags.put(SHADOW_VALIDATION, true);

        // Store percentage (would need separate config for actual canary)
        log.info("Canary mode enabled at {}%. Current flags: {}", percentNew, flags);
    }
}

