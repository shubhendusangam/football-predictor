package com.app.footballprediction.scheduler;

import com.app.common.model.MatchFeatures;
import com.app.common.service.FeatureEngineeringService;
import com.app.common.util.PredictionUtils;
import com.app.common.util.SeasonHelper;
import com.app.footballprediction.dto.external.FootballApiResponse;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.service.FootballDataApiService;
import com.app.footballprediction.service.PredictionTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Scheduled job that auto-generates predictions for upcoming matches.
 *
 * <p>Runs daily at 07:00 AM (configurable) and generates ML-based predictions
 * for all scheduled Premier League matches. Predictions are stored in the
 * database so the dashboard "Today's Predictions" widget always has data.</p>
 *
 * <p>Also runs at startup (with a 60-second delay) to generate predictions
 * immediately if the app was offline.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyPredictionScheduler {

    private final FootballDataApiService footballDataApiService;
    private final FeatureEngineeringService featureEngineeringService;
    private final ModelTrainingService modelTrainingService;
    private final PredictionTrackingService predictionTrackingService;

    @Value("${prediction.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${api.sync.competition:PL}")
    private String competition;

    /**
     * Generate predictions every morning at 7:00 AM.
     * This ensures the "Today's Predictions" widget has data before matches kick off.
     */
    @Scheduled(cron = "${prediction.scheduler.cron:0 0 7 * * *}")
    public void generateDailyPredictions() {
        if (!schedulerEnabled) {
            log.debug("Prediction scheduler disabled, skipping");
            return;
        }
        executePredictionGeneration("SCHEDULED_DAILY");
    }

    /**
     * Also run 60 seconds after startup to catch up if app was offline.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = Long.MAX_VALUE)
    public void generatePredictionsOnStartup() {
        if (!schedulerEnabled) {
            log.debug("Prediction scheduler disabled, skipping startup prediction generation");
            return;
        }
        executePredictionGeneration("STARTUP");
    }

    /**
     * Generate predictions for all upcoming scheduled matches.
     * Can also be called manually via admin API.
     *
     * @return number of predictions generated
     */
    public int executePredictionGeneration(String trigger) {
        LocalDateTime startTime = LocalDateTime.now();

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  🔮 DAILY PREDICTION GENERATION STARTED");
        log.info("  Trigger: {}", trigger);
        log.info("  Time: {}", startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (!modelTrainingService.isModelLoaded()) {
            log.warn("⚠️ Model not loaded — cannot generate predictions");
            return 0;
        }

        int predictionsGenerated = 0;

        try {
            // Fetch upcoming scheduled matches from external API
            FootballApiResponse response = footballDataApiService.getScheduledMatches(competition);

            if (response == null || response.getMatches() == null || response.getMatches().isEmpty()) {
                log.info("ℹ️ No upcoming scheduled matches found for {}", competition);
                return 0;
            }

            List<FootballApiResponse.ApiMatch> scheduledMatches = response.getMatches();
            log.info("Found {} upcoming scheduled matches", scheduledMatches.size());

            for (FootballApiResponse.ApiMatch apiMatch : scheduledMatches) {
                try {
                    int result = generatePredictionForMatch(apiMatch);
                    predictionsGenerated += result;
                } catch (Exception e) {
                    String home = apiMatch.getHomeTeam() != null ? apiMatch.getHomeTeam().getName() : "?";
                    String away = apiMatch.getAwayTeam() != null ? apiMatch.getAwayTeam().getName() : "?";
                    log.warn("Failed to generate prediction for {} vs {}: {}", home, away, e.getMessage());
                }
            }

            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("  ✅ PREDICTION GENERATION COMPLETED");
            log.info("  Matches processed: {}", scheduledMatches.size());
            log.info("  Predictions generated: {}", predictionsGenerated);
            log.info("  Duration: {}ms", duration);
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        } catch (Exception e) {
            log.error("❌ Daily prediction generation failed: {}", e.getMessage(), e);
        }

        return predictionsGenerated;
    }

    /**
     * Generate prediction for a single scheduled match.
     *
     * @return 2 if predictions were generated (home + away), 0 if skipped
     */
    private int generatePredictionForMatch(FootballApiResponse.ApiMatch apiMatch) {
        if (apiMatch.getHomeTeam() == null || apiMatch.getAwayTeam() == null) {
            return 0;
        }

        String homeTeam = footballDataApiService.normalizeTeamName(apiMatch.getHomeTeam().getName());
        String awayTeam = footballDataApiService.normalizeTeamName(apiMatch.getAwayTeam().getName());

        // Parse match date
        LocalDate matchDate = parseMatchDate(apiMatch.getUtcDate());
        String season = SeasonHelper.deriveSeason(matchDate);

        // Build features from historical data
        MatchFeatures features = featureEngineeringService.buildFeaturesForPrediction(homeTeam, awayTeam);

        // Run ML model prediction
        double[] probs;
        try {
            probs = modelTrainingService.predict(features);
        } catch (Exception e) {
            throw new RuntimeException("Model prediction failed for " + homeTeam + " vs " + awayTeam, e);
        }
        String label = modelTrainingService.getPredictedLabel(probs);
        double confidence = Math.max(probs[0], Math.max(probs[1], probs[2]));

        // Record predictions for both teams
        predictionTrackingService.recordMatchPredictions(
                apiMatch.getId(),
                homeTeam,
                awayTeam,
                season,
                matchDate,
                label,
                confidence,
                probs[0],
                probs[1],
                probs[2]
        );

        log.debug("Generated prediction: {} vs {} on {} → {} (confidence: {}%)",
                homeTeam, awayTeam, matchDate,
                PredictionUtils.labelToText(label),
                Math.round(confidence * 100));

        return 2; // one prediction per team
    }

    /**
     * Parse match date from ISO 8601 UTC format.
     */
    private LocalDate parseMatchDate(String utcDate) {
        if (utcDate == null || utcDate.isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(utcDate.substring(0, 10));
        } catch (Exception e) {
            log.warn("Failed to parse match date '{}', using today", utcDate);
            return LocalDate.now();
        }
    }
}


