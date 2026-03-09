package com.app.footballprediction.ingestion.controller;

import com.app.footballprediction.ingestion.config.FeatureFlagService;
import com.app.footballprediction.ingestion.model.IngestionResult;
import com.app.footballprediction.ingestion.orchestrator.IngestionOrchestrator;
import com.app.footballprediction.ingestion.orchestrator.IngestionRouter;
import com.app.footballprediction.ingestion.service.IngestionMetricsService;
import com.app.footballprediction.service.CsvIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin controller for managing the ingestion pipeline.
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Feature flag management</li>
 *   <li>Manual ingestion triggers</li>
 *   <li>Metrics and observability</li>
 *   <li>Provider health checks</li>
 * </ul>
 *
 * <p><b>NOTE:</b> These endpoints should be secured with admin authentication.
 * They are placed under /api/admin/ingestion prefix.
 */
@RestController
@RequestMapping("/api/admin/ingestion")
@RequiredArgsConstructor
@Slf4j
public class IngestionAdminController {

    private final FeatureFlagService featureFlagService;
    private final IngestionOrchestrator orchestrator;
    private final IngestionRouter router;
    private final IngestionMetricsService metricsService;
    private final CsvIngestionService csvIngestionService;

    // ══════════════════════════════════════════════════════════════
    // Feature Flag Endpoints
    // ══════════════════════════════════════════════════════════════

    /**
     * Get all feature flags.
     * GET /api/admin/ingestion/flags
     */
    @GetMapping("/flags")
    public ResponseEntity<Map<String, Boolean>> getFlags() {
        return ResponseEntity.ok(featureFlagService.getAllFlags());
    }

    /**
     * Update a feature flag.
     * PUT /api/admin/ingestion/flags/{flag}
     */
    @PutMapping("/flags/{flag}")
    public ResponseEntity<Map<String, Object>> setFlag(
            @PathVariable String flag,
            @RequestParam boolean value) {

        log.info("Admin updating flag '{}' to {}", flag, value);

        boolean oldValue = featureFlagService.setFlag(flag, value);

        Map<String, Object> response = new HashMap<>();
        response.put("flag", flag);
        response.put("oldValue", oldValue);
        response.put("newValue", value);
        response.put("allFlags", featureFlagService.getAllFlags());

        return ResponseEntity.ok(response);
    }

    /**
     * Rollback to legacy pipeline.
     * POST /api/admin/ingestion/rollback
     */
    @PostMapping("/rollback")
    public ResponseEntity<Map<String, Object>> rollback() {
        log.warn("⚠️ Admin triggered rollback to legacy pipeline");

        featureFlagService.rollbackToLegacy();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "ROLLBACK_COMPLETE");
        response.put("message", "Reverted to legacy ingestion pipeline");
        response.put("flags", featureFlagService.getAllFlags());

        return ResponseEntity.ok(response);
    }

    /**
     * Enable shadow mode.
     * POST /api/admin/ingestion/shadow-mode
     */
    @PostMapping("/shadow-mode")
    public ResponseEntity<Map<String, Object>> enableShadowMode() {
        log.info("Admin enabling shadow mode");

        featureFlagService.enableShadowMode();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SHADOW_MODE_ENABLED");
        response.put("message", "New pipeline running in shadow mode (no writes)");
        response.put("flags", featureFlagService.getAllFlags());

        return ResponseEntity.ok(response);
    }

    /**
     * Enable new pipeline.
     * POST /api/admin/ingestion/enable-new
     */
    @PostMapping("/enable-new")
    public ResponseEntity<Map<String, Object>> enableNewPipeline() {
        log.info("Admin enabling new ingestion pipeline");

        featureFlagService.enableNewPipeline();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "NEW_PIPELINE_ENABLED");
        response.put("message", "New ingestion pipeline is now active");
        response.put("flags", featureFlagService.getAllFlags());

        return ResponseEntity.ok(response);
    }

    // ══════════════════════════════════════════════════════════════
    // Manual Ingestion Endpoints
    // ══════════════════════════════════════════════════════════════

    /**
     * Trigger manual ingestion for a season.
     * POST /api/admin/ingestion/trigger?competition=PL&season=2025-26
     */
    @PostMapping("/trigger")
    public ResponseEntity<IngestionResult> triggerIngestion(
            @RequestParam(defaultValue = "PL") String competition,
            @RequestParam String season) {

        log.info("Admin triggered ingestion for {} {}", competition, season);

        IngestionResult result = orchestrator.ingest(competition, season);

        return ResponseEntity.ok(result);
    }

    /**
     * Trigger ingestion for recent matches.
     * POST /api/admin/ingestion/trigger-recent?competition=PL&limit=20
     */
    @PostMapping("/trigger-recent")
    public ResponseEntity<IngestionResult> triggerRecentIngestion(
            @RequestParam(defaultValue = "PL") String competition,
            @RequestParam(defaultValue = "20") int limit) {

        log.info("Admin triggered recent match ingestion for {} (limit: {})", competition, limit);

        IngestionResult result = orchestrator.ingestRecentMatches(competition, limit);

        return ResponseEntity.ok(result);
    }

    /**
     * Trigger ingestion for scheduled matches.
     * POST /api/admin/ingestion/trigger-scheduled?competition=PL
     */
    @PostMapping("/trigger-scheduled")
    public ResponseEntity<IngestionResult> triggerScheduledIngestion(
            @RequestParam(defaultValue = "PL") String competition) {

        log.info("Admin triggered scheduled match ingestion for {}", competition);

        IngestionResult result = orchestrator.ingestScheduledMatches(competition);

        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════════════
    // Stats Enrichment Endpoints
    // ══════════════════════════════════════════════════════════════

    /**
     * Enrich existing matches with missing statistics from CSV files.
     * The external API only returns scores, not detailed stats (shots, corners, etc.).
     * This re-reads CSV files to fill in any missing statistics.
     * POST /api/admin/ingestion/enrich-stats
     */
    @PostMapping("/enrich-stats")
    public ResponseEntity<Map<String, Object>> enrichStats() {
        log.info("Admin triggered stats enrichment from CSV");

        try {
            int enriched = csvIngestionService.enrichMissingStats();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("matchesEnriched", enriched);
            response.put("message", enriched > 0
                ? enriched + " matches enriched with detailed statistics from CSV"
                : "All matches already have complete statistics");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Stats enrichment failed: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Metrics and Health Endpoints
    // ══════════════════════════════════════════════════════════════

    /**
     * Get ingestion metrics.
     * GET /api/admin/ingestion/metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<IngestionMetricsService.IngestionMetrics> getMetrics() {
        return ResponseEntity.ok(metricsService.getMetrics());
    }

    /**
     * Reset metrics.
     * POST /api/admin/ingestion/metrics/reset
     */
    @PostMapping("/metrics/reset")
    public ResponseEntity<Map<String, String>> resetMetrics() {
        log.info("Admin reset ingestion metrics");
        metricsService.reset();

        return ResponseEntity.ok(Map.of(
            "status", "METRICS_RESET",
            "message", "All ingestion metrics have been reset"
        ));
    }

    /**
     * Get provider status.
     * GET /api/admin/ingestion/providers
     */
    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getProviders() {
        Map<String, Object> response = new HashMap<>();

        router.getAllProviders().forEach(provider -> {
            Map<String, Object> providerInfo = new HashMap<>();
            providerInfo.put("name", provider.getProviderName());
            providerInfo.put("available", provider.isAvailable());
            providerInfo.put("priority", provider.getPriority());
            providerInfo.put("supportedCompetitions", provider.getSupportedCompetitions());

            response.put(provider.getProviderName(), providerInfo);
        });

        return ResponseEntity.ok(response);
    }

    /**
     * Get overall pipeline status.
     * GET /api/admin/ingestion/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();

        // Current pipeline mode
        if (featureFlagService.useLegacyPipeline()) {
            status.put("activePipeline", "LEGACY");
        } else if (featureFlagService.useNewPipeline()) {
            status.put("activePipeline", "NEW");
        } else {
            status.put("activePipeline", "NONE");
        }

        status.put("shadowValidationEnabled", featureFlagService.shadowValidationEnabled());
        status.put("shadowOnly", featureFlagService.isShadowOnly());
        status.put("eventsEnabled", featureFlagService.eventsEnabled());

        // Metrics summary
        var metrics = metricsService.getMetrics();
        status.put("totalIngestions", metrics.getTotalIngestions());
        status.put("successRate", String.format("%.1f%%", metrics.getSuccessRate() * 100));
        status.put("pipelineUsage", metrics.getPipelineUsage());

        // Provider count
        long availableProviders = router.getAllProviders().stream()
            .filter(p -> p.isAvailable())
            .count();
        status.put("availableProviders", availableProviders);
        status.put("totalProviders", router.getAllProviders().size());

        return ResponseEntity.ok(status);
    }
}

