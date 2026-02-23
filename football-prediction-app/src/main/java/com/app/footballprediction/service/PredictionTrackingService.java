package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.Prediction;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.PredictionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for tracking predictions and updating actual results.
 * Handles the lifecycle of predictions from creation to resolution.
 */
@Service
@Slf4j
public class PredictionTrackingService {

    private final PredictionRepository predictionRepository;
    private final MatchRepository matchRepository;
    private final TeamAnalyticsService teamAnalyticsService;

    public PredictionTrackingService(PredictionRepository predictionRepository,
                                      MatchRepository matchRepository,
                                      @Lazy TeamAnalyticsService teamAnalyticsService) {
        this.predictionRepository = predictionRepository;
        this.matchRepository = matchRepository;
        this.teamAnalyticsService = teamAnalyticsService;
    }

    /**
     * Record a new prediction for a match.
     *
     * @param matchId The match ID
     * @param teamName The team this prediction relates to
     * @param opponentName The opponent team
     * @param isHome Whether the team is playing at home
     * @param season The season identifier
     * @param matchDate The match date
     * @param predictedResult The predicted result (WIN, DRAW, LOSS)
     * @param confidence Model confidence (0-1)
     * @param probHomeWin Home win probability
     * @param probDraw Draw probability
     * @param probAwayWin Away win probability
     * @return The created prediction
     */
    @Transactional
    public Prediction recordPrediction(Long matchId, String teamName, String opponentName,
                                        boolean isHome, String season, LocalDate matchDate,
                                        String predictedResult, double confidence,
                                        Double probHomeWin, Double probDraw, Double probAwayWin) {

        // Check if prediction already exists
        Optional<Prediction> existing = predictionRepository.findByMatchIdAndTeamName(matchId, teamName);
        if (existing.isPresent()) {
            log.debug("Prediction already exists for match {} and team {}", matchId, teamName);
            return existing.get();
        }

        Prediction prediction = Prediction.builder()
                .matchId(matchId)
                .teamName(teamName)
                .opponentName(opponentName)
                .isHome(isHome)
                .season(season)
                .matchDate(matchDate)
                .predictedResult(predictedResult)
                .confidence(confidence)
                .probHomeWin(probHomeWin)
                .probDraw(probDraw)
                .probAwayWin(probAwayWin)
                .predictionDate(LocalDateTime.now())
                .build();

        prediction = predictionRepository.save(prediction);
        log.info("Recorded prediction for {} vs {} on {}: {} ({}% confidence)",
                teamName, opponentName, matchDate, predictedResult, Math.round(confidence * 100));

        return prediction;
    }

    /**
     * Record predictions for both teams in a match.
     */
    @Transactional
    public void recordMatchPredictions(Long matchId, String homeTeam, String awayTeam,
                                        String season, LocalDate matchDate,
                                        String predictionCode, double confidence,
                                        Double probHomeWin, Double probDraw, Double probAwayWin) {

        // Determine results for each team based on prediction code
        String homeResult = convertPredictionToTeamResult(predictionCode, true);
        String awayResult = convertPredictionToTeamResult(predictionCode, false);

        // Record for home team
        recordPrediction(matchId, homeTeam, awayTeam, true, season, matchDate,
                homeResult, confidence, probHomeWin, probDraw, probAwayWin);

        // Record for away team
        recordPrediction(matchId, awayTeam, homeTeam, false, season, matchDate,
                awayResult, confidence, probHomeWin, probDraw, probAwayWin);
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

    /**
     * Update prediction with actual result once match is played.
     */
    @Transactional
    public void updatePredictionResult(Long matchId, String teamName, String actualResult,
                                        Integer actualHomeGoals, Integer actualAwayGoals) {

        Optional<Prediction> predOpt = predictionRepository.findByMatchIdAndTeamName(matchId, teamName);
        if (predOpt.isEmpty()) {
            log.debug("No prediction found for match {} and team {}", matchId, teamName);
            return;
        }

        Prediction prediction = predOpt.get();
        prediction.setActualResult(actualResult);
        prediction.setActualHomeGoals(actualHomeGoals);
        prediction.setActualAwayGoals(actualAwayGoals);
        prediction.setIsCorrect(prediction.getPredictedResult().equals(actualResult));
        prediction.setResultRecordedDate(LocalDateTime.now());

        predictionRepository.save(prediction);

        // Evict analytics cache for this team as accuracy data has changed
        try {
            teamAnalyticsService.evictTeamAnalyticsCache(teamName);
        } catch (Exception e) {
            log.warn("Failed to evict analytics cache for {}: {}", teamName, e.getMessage());
        }

        log.info("Updated prediction result for {} match {}: predicted={}, actual={}, correct={}",
                teamName, matchId, prediction.getPredictedResult(), actualResult, prediction.getIsCorrect());
    }

    /**
     * Scheduled task to update predictions with actual results.
     * Runs daily at 6 AM to update results from completed matches.
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void updateUnresolvedPredictions() {
        log.info("Starting scheduled update of unresolved predictions");

        // Get all unresolved predictions where match date has passed
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<Prediction> unresolvedPredictions = predictionRepository
                .findAllUnresolvedPredictionsBeforeDate(yesterday);

        int updated = 0;
        for (Prediction prediction : unresolvedPredictions) {
            // Find the corresponding match
            List<Match> matches = matchRepository.findByTeamBeforeDate(
                    prediction.getTeamName(), yesterday.plusDays(1));

            for (Match match : matches) {
                if (match.getMatchDate().equals(prediction.getMatchDate()) &&
                    (match.getHomeTeam().equalsIgnoreCase(prediction.getTeamName()) ||
                     match.getAwayTeam().equalsIgnoreCase(prediction.getTeamName()))) {

                    // Determine actual result for this team
                    boolean isHome = match.getHomeTeam().equalsIgnoreCase(prediction.getTeamName());
                    String actualResult = determineActualResult(match, isHome);

                    prediction.setActualResult(actualResult);
                    prediction.setActualHomeGoals(match.getFullTimeHomeGoals());
                    prediction.setActualAwayGoals(match.getFullTimeAwayGoals());
                    prediction.setIsCorrect(prediction.getPredictedResult().equals(actualResult));
                    prediction.setResultRecordedDate(LocalDateTime.now());

                    predictionRepository.save(prediction);
                    updated++;
                    break;
                }
            }
        }

        log.info("Updated {} unresolved predictions with actual results", updated);
    }

    /**
     * Determine actual result for a team based on match outcome.
     */
    private String determineActualResult(Match match, boolean isHome) {
        String result = match.getFullTimeResult();
        if ("D".equals(result)) return "DRAW";
        if (isHome) {
            return "H".equals(result) ? "WIN" : "LOSS";
        } else {
            return "A".equals(result) ? "WIN" : "LOSS";
        }
    }

    /**
     * Get prediction accuracy summary for a team.
     */
    public PredictionAccuracySummary getAccuracySummary(String teamName) {
        long total = predictionRepository.countResolvedPredictions(teamName);
        long correct = predictionRepository.countCorrectPredictions(teamName);
        long highConfTotal = predictionRepository.countHighConfidencePredictions(teamName);
        long highConfCorrect = predictionRepository.countCorrectHighConfidencePredictions(teamName);
        long homeTotal = predictionRepository.countHomePredictions(teamName);
        long homeCorrect = predictionRepository.countCorrectHomePredictions(teamName);
        long awayTotal = predictionRepository.countAwayPredictions(teamName);
        long awayCorrect = predictionRepository.countCorrectAwayPredictions(teamName);
        Double avgConfidence = predictionRepository.getAverageConfidence(teamName);

        return new PredictionAccuracySummary(
                total, correct,
                total > 0 ? (double) correct / total * 100 : 0,
                highConfTotal, highConfCorrect,
                highConfTotal > 0 ? (double) highConfCorrect / highConfTotal * 100 : 0,
                homeTotal, homeCorrect,
                homeTotal > 0 ? (double) homeCorrect / homeTotal * 100 : 0,
                awayTotal, awayCorrect,
                awayTotal > 0 ? (double) awayCorrect / awayTotal * 100 : 0,
                avgConfidence != null ? avgConfidence * 100 : 0
        );
    }

    /**
     * Summary record for prediction accuracy.
     */
    public record PredictionAccuracySummary(
            long totalPredictions,
            long correctPredictions,
            double overallAccuracy,
            long highConfidencePredictions,
            long correctHighConfidencePredictions,
            double highConfidenceAccuracy,
            long homePredictions,
            long correctHomePredictions,
            double homeAccuracy,
            long awayPredictions,
            long correctAwayPredictions,
            double awayAccuracy,
            double averageConfidence
    ) {}
}

