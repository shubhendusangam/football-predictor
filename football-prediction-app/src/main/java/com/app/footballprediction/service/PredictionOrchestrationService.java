package com.app.footballprediction.service;

import com.app.common.model.MatchFeatures;
import com.app.common.service.FeatureEngineeringService;
import com.app.common.util.PredictionUtils;
import com.app.footballprediction.dto.H2HInsightsResponse;
import com.app.footballprediction.dto.PredictResponse;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Orchestrates the full prediction pipeline:
 * feature building → base model → Elo adjustment → H2H enrichment → response assembly.
 *
 * Extracted from PredictionController to keep the controller thin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionOrchestrationService {

    private final FeatureEngineeringService featureEngineeringService;
    private final ModelTrainingService modelTrainingService;
    private final EloPredictionService eloPredictionService;
    private final H2HInsightsService h2hInsightsService;
    private final TrendingInsightsService trendingInsightsService;

    /**
     * Run the full prediction pipeline for a home/away pair.
     *
     * @param homeTeam normalized home team name
     * @param awayTeam normalized away team name
     * @return fully assembled prediction response
     */
    public PredictResponse predict(String homeTeam, String awayTeam) {
        // ── Build features from DB history ─────────────────────
        MatchFeatures features = featureEngineeringService
                .buildFeaturesForPrediction(homeTeam, awayTeam);

        log.debug("Features built → homeForm:{} awayForm:{} h2h:{}/{}/{}",
                PredictionUtils.round(features.getHomeFormPoints()),
                PredictionUtils.round(features.getAwayFormPoints()),
                PredictionUtils.round(features.getH2hHomeWinRate()),
                PredictionUtils.round(features.getH2hDrawRate()),
                PredictionUtils.round(features.getH2hAwayWinRate()));

        // ── Run base model ─────────────────────────────────────
        double[] baseProbs;
        try {
            baseProbs = modelTrainingService.predict(features);
        } catch (Exception e) {
            throw new RuntimeException("Prediction failed: " + e.getMessage(), e);
        }

        // ── Apply Elo adjustments ──────────────────────────────
        String currentSeason = trendingInsightsService.getCurrentSeason();
        EloPredictionService.EloPredictionResult eloResult = eloPredictionService
                .calculateEloPrediction(homeTeam, awayTeam, currentSeason, baseProbs, features);

        double[] probes = {
                eloResult.getHomeWinProbability(),
                eloResult.getDrawProbability(),
                eloResult.getAwayWinProbability()
        };
        String label = modelTrainingService.getPredictedLabel(probes);

        log.debug("Elo adjusted → Home Elo:{} Away Elo:{} Diff:{} Upset:{}",
                eloResult.getHomeElo(), eloResult.getAwayElo(),
                eloResult.getEloDifference(), eloResult.isUpsetAlert());

        // ── Get enhanced H2H insights ──────────────────────────
        H2HInsightsResponse h2hFull = h2hInsightsService.getH2HInsights(homeTeam, awayTeam);
        PredictResponse.H2HSummary h2hSummary = buildH2HSummary(h2hFull);

        // ── Pre-Match Insights ─────────────────────────────────
        double homeGoalThreat = clamp(
                (features.getHomeGoalsScoredAvg() * 30) + (features.getAwayGoalsConcededAvg() * 20));
        double awayGoalThreat = clamp(
                (features.getAwayGoalsScoredAvg() * 30) + (features.getHomeGoalsConcededAvg() * 20));

        // ── Assemble response ──────────────────────────────────
        PredictResponse response = PredictResponse.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .prediction(PredictionUtils.labelToText(label))
                .predictionCode(label)
                .probHomeWin(PredictionUtils.round(probes[0]))
                .probDraw(PredictionUtils.round(probes[1]))
                .probAwayWin(PredictionUtils.round(probes[2]))
                .confidence(PredictionUtils.getConfidence(probes))
                .homeElo(PredictionUtils.round(eloResult.getHomeElo()))
                .awayElo(PredictionUtils.round(eloResult.getAwayElo()))
                .eloDifference(PredictionUtils.round(eloResult.getEloDifference()))
                .upsetAlert(eloResult.isUpsetAlert())
                .upsetTeam(eloResult.getUpsetTeam())
                .explanation(eloResult.getExplanation())
                .features(PredictResponse.FeatureSummary.builder()
                        .homeFormPoints(PredictionUtils.round(features.getHomeFormPoints()))
                        .awayFormPoints(PredictionUtils.round(features.getAwayFormPoints()))
                        .homeGoalsScoredAvg(PredictionUtils.round(features.getHomeGoalsScoredAvg()))
                        .awayGoalsScoredAvg(PredictionUtils.round(features.getAwayGoalsScoredAvg()))
                        .h2hHomeWinRate(PredictionUtils.round(features.getH2hHomeWinRate()))
                        .h2hDrawRate(PredictionUtils.round(features.getH2hDrawRate()))
                        .h2hAwayWinRate(PredictionUtils.round(features.getH2hAwayWinRate()))
                        .homeGoalsConcededAvg(PredictionUtils.round(features.getHomeGoalsConcededAvg()))
                        .awayGoalsConcededAvg(PredictionUtils.round(features.getAwayGoalsConcededAvg()))
                        .homeWinStreak(features.getHomeWinStreak())
                        .awayWinStreak(features.getAwayWinStreak())
                        .homeUnbeatenStreak(features.getHomeUnbeatenStreak())
                        .awayUnbeatenStreak(features.getAwayUnbeatenStreak())
                        .homeDaysSinceLastMatch(features.getHomeDaysSinceLastMatch())
                        .awayDaysSinceLastMatch(features.getAwayDaysSinceLastMatch())
                        .homeGoalThreat(PredictionUtils.round(homeGoalThreat))
                        .awayGoalThreat(PredictionUtils.round(awayGoalThreat))
                        .build())
                .h2hInsights(h2hSummary)
                .build();

        log.info("Predicted: {} vs {} → {} (H:{} D:{} A:{}) Elo diff:{}",
                homeTeam, awayTeam, response.getPrediction(),
                PredictionUtils.round(probes[0]),
                PredictionUtils.round(probes[1]),
                PredictionUtils.round(probes[2]),
                eloResult.getEloDifference());

        return response;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private PredictResponse.H2HSummary buildH2HSummary(H2HInsightsResponse h2h) {
        if (h2h == null || h2h.getHistoricalRecord() == null) {
            return null;
        }

        var historical = h2h.getHistoricalRecord();
        var goalStats = h2h.getGoalStats();
        var commonResults = h2h.getCommonResults();
        var venueAdvantage = h2h.getVenueAdvantage();

        var recentMeetings = h2h.getRecentMeetings() != null
                ? h2h.getRecentMeetings().stream()
                .map(m -> PredictResponse.RecentH2HMatch.builder()
                        .date(m.getDate())
                        .homeTeamInMatch(m.getHomeTeamInMatch())
                        .awayTeamInMatch(m.getAwayTeamInMatch())
                        .score(m.getScore())
                        .winner(m.getWinner())
                        .season(m.getSeason())
                        .build())
                .toList()
                : Collections.<PredictResponse.RecentH2HMatch>emptyList();

        return PredictResponse.H2HSummary.builder()
                .historicalRecord(historical.getSummary())
                .totalMeetings(historical.getTotalMatches())
                .homeTeamWins(historical.getHomeTeamWins())
                .draws(historical.getDraws())
                .awayTeamWins(historical.getAwayTeamWins())
                .dominantTeam(historical.getDominantTeam())
                .recentMeetings(recentMeetings)
                .avgGoalsPerMatch(goalStats != null ? goalStats.getAvgTotalGoals() : 0)
                .avgHomeTeamGoals(goalStats != null ? goalStats.getAvgHomeTeamGoals() : 0)
                .avgAwayTeamGoals(goalStats != null ? goalStats.getAvgAwayTeamGoals() : 0)
                .mostCommonScore(commonResults != null ? commonResults.getMostCommonResult() : "N/A")
                .mostCommonOutcome(commonResults != null ? commonResults.getMostCommonOutcome() : "N/A")
                .homeTeamHomeWinPct(venueAdvantage != null ? venueAdvantage.getHomeTeamHomeWinPercentage() : 0)
                .awayTeamHomeWinPct(venueAdvantage != null ? venueAdvantage.getAwayTeamHomeWinPercentage() : 0)
                .venueAdvantageNote(venueAdvantage != null ? venueAdvantage.getHomeAdvantageDescription() : "N/A")
                .build();
    }

    private static double clamp(double value) {
        return Math.min(100, Math.max(0, value));
    }
}

