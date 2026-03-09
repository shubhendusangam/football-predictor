package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.Prediction;
import com.app.common.model.PredictionEvaluation;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.PredictionEvaluationRepository;
import com.app.common.repository.PredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service that detects completed matches and triggers the recalculation pipeline.
 *
 * <p><strong>IMPORTANT — Match ID mapping:</strong></p>
 * <p>Prediction.matchId stores the <em>external football-data.org API match ID</em>,
 * while Match.id is the <em>local auto-generated primary key</em>. These are NOT the same.
 * All look-ups therefore match predictions to matches via <strong>team name + match date</strong>.</p>
 *
 * Responsibilities:
 * - Detect matches where fullTimeResult is not null
 * - Find corresponding predictions via team name + date
 * - Create PredictionEvaluation records comparing predicted vs actual
 * - Resolve Prediction records (set actualResult, isCorrect, etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchResultProcessor {

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final PredictionEvaluationRepository evaluationRepository;

    /** Batch size for saving predictions and evaluations */
    private static final int BATCH_SIZE = 50;

    // ═══════════════════════════════════════════════════════════════════
    // Backfill — resolves ALL historical unresolved predictions
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Process the entire backlog of unresolved predictions.
     * Finds each prediction's corresponding finished match by team name + date,
     * creates PredictionEvaluation records, and marks the predictions as resolved.
     *
     * <p><strong>Optimizations:</strong></p>
     * <ul>
     *   <li>Pre-loads all finished matches into a HashMap for O(1) lookup
     *       instead of 1-2 DB queries per prediction</li>
     *   <li>Pre-loads evaluated match IDs to skip already-evaluated matches</li>
     *   <li>Batch-saves resolved predictions and evaluations</li>
     * </ul>
     *
     * <p>This is the primary method for populating accuracy data — it must be run
     * at least once on startup so the UI shows real accuracy numbers.</p>
     *
     * @return number of predictions resolved
     */
    @Transactional
    public int processAllUnresolvedPredictions() {
        LocalDate today = LocalDate.now();
        List<Prediction> unresolved = predictionRepository
                .findAllUnresolvedPredictionsBeforeDate(today);

        if (unresolved.isEmpty()) {
            log.debug("No unresolved predictions to backfill");
            return 0;
        }

        log.debug("Backfilling {} unresolved predictions against finished matches...", unresolved.size());
        long startTime = System.currentTimeMillis();

        // ── Pre-load all finished matches into a lookup map (1 query) ─────
        // Key: "homeTeam|awayTeam|matchDate" (lowercase for case-insensitive matching)
        List<Match> allFinished = matchRepository.findAllFinishedMatchesBeforeDate(today);
        Map<String, Match> matchLookup = new HashMap<>(allFinished.size());
        for (Match m : allFinished) {
            String key = buildMatchKey(m.getHomeTeam(), m.getAwayTeam(), m.getMatchDate());
            matchLookup.put(key, m);
        }
        log.debug("Pre-loaded {} finished matches into lookup map", matchLookup.size());

        int resolved = 0;
        int evaluationsCreated = 0;
        Set<Long> evaluatedMatchIds = new LinkedHashSet<>();

        // Pre-load existing evaluations to avoid per-match DB checks
        List<PredictionEvaluation> existingEvals = evaluationRepository.findAll();
        Set<Long> existingEvalMatchIds = new java.util.HashSet<>();
        for (PredictionEvaluation eval : existingEvals) {
            existingEvalMatchIds.add(eval.getMatchId());
        }

        List<Prediction> pendingPredictions = new ArrayList<>(BATCH_SIZE);
        List<PredictionEvaluation> pendingEvaluations = new ArrayList<>(BATCH_SIZE);

        for (int i = 0; i < unresolved.size(); i++) {
            Prediction prediction = unresolved.get(i);
            try {
                Match match = findMatchInMap(prediction, matchLookup);
                if (match == null) continue;

                // ── Resolve the Prediction record ────────────────────────
                resolvePredictionInMemory(prediction, match);
                pendingPredictions.add(prediction);
                resolved++;

                // ── Create PredictionEvaluation (once per local match ID) ─
                if (!evaluatedMatchIds.contains(match.getId())
                        && !existingEvalMatchIds.contains(match.getId())) {
                    PredictionEvaluation eval = buildEvaluation(match, prediction);
                    if (eval != null) {
                        pendingEvaluations.add(eval);
                        evaluationsCreated++;
                        evaluatedMatchIds.add(match.getId());
                        existingEvalMatchIds.add(match.getId());
                    }
                }

                // Flush batches periodically
                if (pendingPredictions.size() >= BATCH_SIZE) {
                    predictionRepository.saveAll(pendingPredictions);
                    pendingPredictions.clear();
                }
                if (pendingEvaluations.size() >= BATCH_SIZE) {
                    evaluationRepository.saveAll(pendingEvaluations);
                    pendingEvaluations.clear();
                }
            } catch (Exception e) {
                log.warn("Failed to resolve prediction id={} ({} on {}): {}",
                        prediction.getId(), prediction.getTeamName(),
                        prediction.getMatchDate(), e.getMessage());
            }

            // Progress logging every 500 predictions
            if ((i + 1) % 500 == 0) {
                log.info("  Backfill progress: {}/{} predictions processed ({} resolved)",
                        i + 1, unresolved.size(), resolved);
            }
        }

        // Flush remaining batches
        if (!pendingPredictions.isEmpty()) {
            predictionRepository.saveAll(pendingPredictions);
        }
        if (!pendingEvaluations.isEmpty()) {
            evaluationRepository.saveAll(pendingEvaluations);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Backfill complete: resolved {}/{} predictions, created {} evaluations, duration={}ms",
                resolved, unresolved.size(), evaluationsCreated, duration);
        return resolved;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Date-scoped processing (used by the scheduler)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Find finished matches on a specific date that have not been evaluated yet.
     */
    public List<Match> findNewFinishedMatchesForDate(LocalDate date) {
        List<Match> finishedToday = matchRepository.findFinishedMatchesByDate(date);

        List<Match> unevaluated = new ArrayList<>();
        for (Match match : finishedToday) {
            if (!evaluationRepository.existsByMatchId(match.getId())) {
                unevaluated.add(match);
            }
        }

        log.info("Found {} new finished matches on {} (total finished: {})",
                unevaluated.size(), date, finishedToday.size());
        return unevaluated;
    }

    /**
     * Process finished matches for a specific date.
     * Resolves predictions and creates evaluations.
     *
     * @return number of evaluations created
     */
    @Transactional
    public int processFinishedMatchesForDate(LocalDate date) {
        List<Match> newFinished = findNewFinishedMatchesForDate(date);
        if (newFinished.isEmpty()) {
            log.debug("No new finished matches to evaluate on {}", date);
            return 0;
        }

        int evaluationsCreated = 0;
        for (Match match : newFinished) {
            try {
                int count = evaluateMatchAndResolvePredictions(match);
                evaluationsCreated += count;
            } catch (Exception e) {
                log.error("Failed to process match {} ({} vs {}): {}",
                        match.getId(), match.getHomeTeam(), match.getAwayTeam(), e.getMessage());
            }
        }

        log.info("Created {} evaluations for {} on {}", evaluationsCreated, newFinished.size(), date);
        return evaluationsCreated;
    }

    /**
     * Legacy entry point: processes the entire backlog via team+date matching.
     * Kept for manual triggers and backward compatibility.
     *
     * @return number of predictions resolved
     */
    @Transactional
    public int processFinishedMatches() {
        return processAllUnresolvedPredictions();
    }

    /**
     * @deprecated Use {@link #findNewFinishedMatchesForDate(LocalDate)} instead.
     */
    @Deprecated
    public List<Match> detectFinishedUnevaluatedMatches() {
        return findNewFinishedMatchesForDate(LocalDate.now());
    }

    // ═══════════════════════════════════════════════════════════════════
    // DB-status helpers (used by scheduler)
    // ═══════════════════════════════════════════════════════════════════

    public boolean hasLiveOrPendingMatches(LocalDate date) {
        return matchRepository.countUnfinishedMatchesByDate(date) > 0;
    }

    public boolean hasFinishedMatchesToday(LocalDate date) {
        return matchRepository.countFinishedMatchesByDate(date) > 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Core evaluation logic
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Evaluate a match: find all predictions for it (by team+date), resolve each,
     * and create one PredictionEvaluation record using the home-team prediction.
     *
     * @return 1 if an evaluation was created, 0 otherwise
     */
    private int evaluateMatchAndResolvePredictions(Match match) {
        if (match.getFullTimeResult() == null) return 0;
        if (evaluationRepository.existsByMatchId(match.getId())) return 0;

        String actualWinner = match.getFullTimeResult(); // H, D, or A

        // Find & resolve both home and away predictions
        Prediction homePred = findAndResolve(match.getHomeTeam(), match.getMatchDate(), match, actualWinner);
        Prediction awayPred = findAndResolve(match.getAwayTeam(), match.getMatchDate(), match, actualWinner);

        // Pick the best prediction for the evaluation record (prefer home)
        Prediction evalSource = homePred != null ? homePred : awayPred;
        if (evalSource == null) {
            log.debug("No prediction for {} vs {} on {}", match.getHomeTeam(), match.getAwayTeam(), match.getMatchDate());
            return 0;
        }

        PredictionEvaluation eval = createEvaluation(match, evalSource);
        return eval != null ? 1 : 0;
    }

    /**
     * Find the prediction for a given team on a given date and resolve it
     * (set actualResult, isCorrect, etc.). Returns null if not found.
     */
    private Prediction findAndResolve(String teamName, LocalDate matchDate,
                                       Match match, String actualWinner) {
        List<Prediction> candidates = predictionRepository
                .findByTeamNameIgnoreCaseOrderByMatchDateDesc(teamName);

        for (Prediction p : candidates) {
            if (p.getMatchDate() != null && p.getMatchDate().equals(matchDate) && !p.isResolved()) {
                resolvePrediction(p, match);
                return p;
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Map-based lookup (used by optimized backfill)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Build a case-insensitive lookup key for a match.
     */
    private String buildMatchKey(String homeTeam, String awayTeam, LocalDate date) {
        return (homeTeam + "|" + awayTeam + "|" + date).toLowerCase();
    }

    /**
     * Find the match for a prediction using the pre-loaded map.
     * Tries both orientations (team as home, team as away).
     * O(1) instead of 1-2 DB queries.
     */
    private Match findMatchInMap(Prediction prediction, Map<String, Match> matchLookup) {
        if (prediction.getMatchDate() == null || prediction.getTeamName() == null) return null;

        String teamName = prediction.getTeamName();
        String opponentName = prediction.getOpponentName();
        LocalDate matchDate = prediction.getMatchDate();

        // Try team as home
        if (opponentName != null) {
            String key1 = buildMatchKey(teamName, opponentName, matchDate);
            Match match = matchLookup.get(key1);
            if (match != null && match.getFullTimeResult() != null) return match;

            // Try team as away
            String key2 = buildMatchKey(opponentName, teamName, matchDate);
            match = matchLookup.get(key2);
            if (match != null && match.getFullTimeResult() != null) return match;
        }

        return null;
    }

    /**
     * Resolve a prediction in memory without saving to DB.
     * Used by the batch backfill path — the caller handles batch saves.
     */
    private void resolvePredictionInMemory(Prediction prediction, Match match) {
        String actualWinner = match.getFullTimeResult();
        boolean isHome = prediction.isHome();
        String actualResult;
        if ("D".equals(actualWinner)) {
            actualResult = "DRAW";
        } else if (isHome) {
            actualResult = "H".equals(actualWinner) ? "WIN" : "LOSS";
        } else {
            actualResult = "A".equals(actualWinner) ? "WIN" : "LOSS";
        }

        prediction.setActualResult(actualResult);
        prediction.setActualHomeGoals(match.getFullTimeHomeGoals());
        prediction.setActualAwayGoals(match.getFullTimeAwayGoals());
        prediction.setIsCorrect(prediction.getPredictedResult().equals(actualResult));
        prediction.setResultRecordedDate(LocalDateTime.now());
    }

    /**
     * Find the match that corresponds to a prediction using team name + date.
     * Uses case-insensitive matching for robustness.
     * Returns null if the match hasn't been played yet.
     * Used by the date-scoped path (scheduler) only.
     */
    private Match findMatchForPrediction(Prediction prediction) {
        if (prediction.getMatchDate() == null || prediction.getTeamName() == null) return null;

        // Try case-insensitive lookup: prediction team as home
        Match match = matchRepository.findByMatchDateAndHomeTeamIgnoreCaseAndAwayTeamIgnoreCase(
                prediction.getMatchDate(), prediction.getTeamName(), prediction.getOpponentName());

        if (match == null && prediction.getOpponentName() != null) {
            // Try the reverse (the prediction's team might be away)
            match = matchRepository.findByMatchDateAndHomeTeamIgnoreCaseAndAwayTeamIgnoreCase(
                    prediction.getMatchDate(), prediction.getOpponentName(), prediction.getTeamName());
        }

        if (match != null && match.getFullTimeResult() != null) {
            return match;
        }
        return null;
    }

    /**
     * Mark a Prediction as resolved with the actual match result and save immediately.
     * Used by the date-scoped path (scheduler).
     */
    private void resolvePrediction(Prediction prediction, Match match) {
        resolvePredictionInMemory(prediction, match);
        predictionRepository.save(prediction);
    }

    /**
     * Build a PredictionEvaluation in memory without saving.
     * Used by the batch backfill path — the caller handles batch saves.
     */
    private PredictionEvaluation buildEvaluation(Match match, Prediction prediction) {
        String predictedWinner = determinePredictedWinner(prediction);
        String actualWinner = match.getFullTimeResult();

        boolean winnerCorrect = predictedWinner != null && predictedWinner.equals(actualWinner);
        boolean scoreExact = prediction.getPredictedHomeGoals() != null
                && prediction.getPredictedAwayGoals() != null
                && prediction.getPredictedHomeGoals().equals(match.getFullTimeHomeGoals())
                && prediction.getPredictedAwayGoals().equals(match.getFullTimeAwayGoals());

        return PredictionEvaluation.builder()
                .matchId(match.getId())
                .predictedHomeGoals(prediction.getPredictedHomeGoals())
                .predictedAwayGoals(prediction.getPredictedAwayGoals())
                .actualHomeGoals(match.getFullTimeHomeGoals())
                .actualAwayGoals(match.getFullTimeAwayGoals())
                .predictedWinner(predictedWinner)
                .actualWinner(actualWinner)
                .goalDifferenceError(calculateGoalDifferenceError(prediction, match))
                .winnerCorrect(winnerCorrect)
                .scoreExact(scoreExact)
                .cardPredictionError(calculateCardError(match))
                .cornerPredictionError(calculateCornerError(match))
                .homeTeam(match.getHomeTeam())
                .awayTeam(match.getAwayTeam())
                .season(match.getSeason())
                .predictionConfidence(prediction.getConfidence())
                .evaluationTime(LocalDateTime.now())
                .build();
    }

    /**
     * Create a PredictionEvaluation from a match + a representative prediction and save it.
     * Used by the date-scoped path (scheduler).
     */
    private PredictionEvaluation createEvaluation(Match match, Prediction prediction) {
        PredictionEvaluation evaluation = buildEvaluation(match, prediction);
        if (evaluation == null) return null;

        evaluation = evaluationRepository.save(evaluation);

        log.debug("Match: {} vs {} | Prediction: {}-{} | Actual: {}-{} | WinnerCorrect: {} | GoalError: {} | ScoreExact: {}",
                match.getHomeTeam(), match.getAwayTeam(),
                prediction.getPredictedHomeGoals(), prediction.getPredictedAwayGoals(),
                match.getFullTimeHomeGoals(), match.getFullTimeAwayGoals(),
                evaluation.getWinnerCorrect(), evaluation.getGoalDifferenceError(), evaluation.getScoreExact());

        return evaluation;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Calculation helpers
    // ═══════════════════════════════════════════════════════════════════

    private String determinePredictedWinner(Prediction prediction) {
        if (prediction.getPredictedHomeGoals() != null && prediction.getPredictedAwayGoals() != null) {
            int h = prediction.getPredictedHomeGoals(), a = prediction.getPredictedAwayGoals();
            if (h > a) return "H";
            if (a > h) return "A";
            return "D";
        }
        String result = prediction.getPredictedResult();
        if (result == null) return null;
        if (prediction.isHome()) {
            return switch (result) { case "WIN" -> "H"; case "LOSS" -> "A"; case "DRAW" -> "D"; default -> null; };
        } else {
            return switch (result) { case "WIN" -> "A"; case "LOSS" -> "H"; case "DRAW" -> "D"; default -> null; };
        }
    }

    private Integer calculateGoalDifferenceError(Prediction prediction, Match match) {
        if (prediction.getPredictedHomeGoals() == null || prediction.getPredictedAwayGoals() == null
                || match.getFullTimeHomeGoals() == null || match.getFullTimeAwayGoals() == null) return null;
        return Math.abs(
                (prediction.getPredictedHomeGoals() - prediction.getPredictedAwayGoals())
              - (match.getFullTimeHomeGoals() - match.getFullTimeAwayGoals()));
    }

    private Integer calculateCardError(Match match) {
        if (match.getHomeYellowCards() == null && match.getAwayYellowCards() == null) return null;
        int actual = safeInt(match.getHomeYellowCards()) + safeInt(match.getAwayYellowCards())
                + safeInt(match.getHomeRedCards()) + safeInt(match.getAwayRedCards());
        return Math.abs(4 - actual); // 4 = league-average estimate
    }

    private Integer calculateCornerError(Match match) {
        if (match.getHomeCorners() == null && match.getAwayCorners() == null) return null;
        int actual = safeInt(match.getHomeCorners()) + safeInt(match.getAwayCorners());
        return Math.abs(10 - actual); // 10 = league-average estimate
    }

    private int safeInt(Integer val) { return val != null ? val : 0; }
}

