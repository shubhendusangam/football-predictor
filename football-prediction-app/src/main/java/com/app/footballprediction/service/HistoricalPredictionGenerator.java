package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.MatchFeatures;
import com.app.common.model.Prediction;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.PredictionRepository;
import com.app.common.service.FeatureEngineeringService;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.util.SeasonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generates Prediction records for historical (already-finished) matches that
 * don't yet have predictions.
 *
 * <p>This solves the "Model Accuracy tab is empty" problem: predictions are
 * normally only created when upcoming matches are fetched via the external API.
 * Historical matches ingested from CSV or past API syncs never had predictions
 * recorded, so there is nothing for the backfill pipeline to resolve.</p>
 *
 * <p>The generator:</p>
 * <ol>
 *   <li>Bulk-loads all match IDs that already have predictions (single query)</li>
 *   <li>Queries only finished matches that are NOT in that set (single query)</li>
 *   <li>Builds features and runs the model for each unprocessed match</li>
 *   <li>Batch-saves Prediction records for efficiency</li>
 * </ol>
 *
 * <p>After this runs, {@link MatchResultProcessor#processAllUnresolvedPredictions()}
 * can resolve them against the actual results and populate accuracy data.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalPredictionGenerator {

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final FeatureEngineeringService featureEngineeringService;
    private final ModelTrainingService modelTrainingService;

    /** Batch size for saving predictions */
    private static final int BATCH_SIZE = 50;

    /**
     * Generate predictions for all finished matches that lack prediction records.
     *
     * <p><strong>Optimizations over the original implementation:</strong></p>
     * <ul>
     *   <li>Single bulk query to get all match IDs with existing predictions</li>
     *   <li>DB-level filtering to fetch only unprocessed matches (instead of loading ALL matches)</li>
     *   <li>Batch-save predictions instead of individual saves</li>
     *   <li>Progress logging every 100 matches</li>
     * </ul>
     *
     * @return number of matches for which predictions were generated
     */
    @Transactional
    public int generateAll() {
        if (!modelTrainingService.isModelLoaded()) {
            log.warn("Model not loaded — skipping historical prediction generation");
            return 0;
        }

        // ── Step 1: Bulk-load match IDs that already have predictions (1 query) ──
        Set<Long> matchIdsWithPredictions = predictionRepository.findAllDistinctMatchIds();
        log.debug("Found {} match IDs with existing predictions", matchIdsWithPredictions.size());

        // ── Step 2: Fetch only finished matches that DON'T have predictions (1 query) ──
        List<Match> unprocessedMatches;
        if (matchIdsWithPredictions.isEmpty()) {
            unprocessedMatches = matchRepository.findAllFinishedMatchesAsc();
        } else {
            unprocessedMatches = matchRepository.findFinishedMatchesExcludingIds(matchIdsWithPredictions);
        }

        if (unprocessedMatches.isEmpty()) {
            log.debug("All finished matches already have predictions — nothing to generate");
            return 0;
        }

        log.info("Generating predictions for {} unprocessed finished matches...", unprocessedMatches.size());
        long startTime = System.currentTimeMillis();

        // ── Step 3: Process matches in batches with progress logging ──
        int generated = 0;
        int skipped = 0;
        int total = unprocessedMatches.size();
        List<Prediction> pendingBatch = new ArrayList<>(BATCH_SIZE * 2);

        for (int i = 0; i < total; i++) {
            Match match = unprocessedMatches.get(i);
            try {
                List<Prediction> predictions = buildPredictionsForMatch(match);
                if (predictions != null && !predictions.isEmpty()) {
                    pendingBatch.addAll(predictions);
                    generated++;

                    // Flush batch when full
                    if (pendingBatch.size() >= BATCH_SIZE) {
                        predictionRepository.saveAll(pendingBatch);
                        pendingBatch.clear();
                    }
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.debug("Skipped {} vs {} ({}): {}",
                        match.getHomeTeam(), match.getAwayTeam(),
                        match.getMatchDate(), e.getMessage());
                skipped++;
            }

            // Progress logging every 100 matches
            if ((i + 1) % 100 == 0 || i == total - 1) {
                long elapsed = System.currentTimeMillis() - startTime;
                double rate = elapsed > 0 ? (i + 1) / (elapsed / 1000.0) : 0;
                int remaining = total - (i + 1);
                long eta = rate > 0 ? (long) (remaining / rate) : 0;
                log.info("  Progress: {}/{} matches ({} generated, {} skipped) — {} matches/sec, ETA: {}s",
                        i + 1, total, generated, skipped, String.format("%.0f", rate), eta);
            }
        }

        // Flush remaining batch
        if (!pendingBatch.isEmpty()) {
            predictionRepository.saveAll(pendingBatch);
            pendingBatch.clear();
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Historical prediction generation complete: generated={}, skipped={}, total={}, duration={}ms",
                generated, skipped, total, duration);
        return generated;
    }

    /**
     * Build prediction entities for a single historical match.
     * Returns a list of Prediction entities (home + away) ready for batch save,
     * or null if predictions couldn't be generated.
     */
    private List<Prediction> buildPredictionsForMatch(Match match) throws Exception {
        String homeTeam = match.getHomeTeam();
        String awayTeam = match.getAwayTeam();
        LocalDate matchDate = match.getMatchDate();
        String season = match.getSeason() != null ? match.getSeason()
                : SeasonUtils.getCurrentSeason(matchDate);

        // Build features using only data available BEFORE the match
        MatchFeatures features = featureEngineeringService
                .buildFeaturesForHistoricalPrediction(homeTeam, awayTeam, matchDate, season);

        // Run model
        double[] probs = modelTrainingService.predict(features);
        String label = modelTrainingService.getPredictedLabel(probs);
        double confidence = Math.max(probs[0], Math.max(probs[1], probs[2]));

        // Convert prediction code to team-specific results
        String homeResult = convertPredictionToTeamResult(label, true);
        String awayResult = convertPredictionToTeamResult(label, false);

        List<Prediction> predictions = new ArrayList<>(2);

        // Check if predictions already exist (safety check for concurrent runs)
        if (!predictionRepository.existsByMatchIdAndTeamName(match.getId(), homeTeam)) {
            predictions.add(Prediction.builder()
                    .matchId(match.getId())
                    .teamName(homeTeam)
                    .opponentName(awayTeam)
                    .isHome(true)
                    .season(season)
                    .matchDate(matchDate)
                    .predictedResult(homeResult)
                    .confidence(confidence)
                    .probHomeWin(probs[0])
                    .probDraw(probs[1])
                    .probAwayWin(probs[2])
                    .predictionDate(LocalDateTime.now())
                    .build());
        }

        if (!predictionRepository.existsByMatchIdAndTeamName(match.getId(), awayTeam)) {
            predictions.add(Prediction.builder()
                    .matchId(match.getId())
                    .teamName(awayTeam)
                    .opponentName(homeTeam)
                    .isHome(false)
                    .season(season)
                    .matchDate(matchDate)
                    .predictedResult(awayResult)
                    .confidence(confidence)
                    .probHomeWin(probs[0])
                    .probDraw(probs[1])
                    .probAwayWin(probs[2])
                    .predictionDate(LocalDateTime.now())
                    .build());
        }

        return predictions;
    }

    /**
     * Convert prediction code (H/D/A) to team-specific result (WIN/DRAW/LOSS).
     */
    private String convertPredictionToTeamResult(String predictionCode, boolean isHome) {
        if (predictionCode == null) return "DRAW";
        return switch (predictionCode) {
            case "H" -> isHome ? "WIN" : "LOSS";
            case "A" -> isHome ? "LOSS" : "WIN";
            case "D" -> "DRAW";
            default -> "DRAW";
        };
    }
}


