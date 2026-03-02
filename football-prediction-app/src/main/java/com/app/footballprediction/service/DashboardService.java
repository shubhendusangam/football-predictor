package com.app.footballprediction.service;

import com.app.common.model.LeagueStanding;
import com.app.common.model.Match;
import com.app.common.model.Prediction;
import com.app.common.repository.LeagueStandingRepository;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.PredictionRepository;
import com.app.footballprediction.config.CacheConfig;
import com.app.footballprediction.dto.dashboard.ModelAccuracyResponse;
import com.app.footballprediction.dto.dashboard.TodaysPredictionsResponse;
import com.app.footballprediction.dto.dashboard.TopTeamsResponse;
import com.app.footballprediction.dto.dashboard.UpcomingMatchesResponse;
import com.app.footballprediction.dto.external.FootballApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for dashboard-specific data aggregation.
 * Optimized for fast response times (<300ms) with caching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final LeagueStandingRepository standingRepository;
    private final LeagueStandingService leagueStandingService;
    private final FootballDataApiService footballDataApiService;

    private static final long DEFAULT_LEAGUE_ID = 1L; // Premier League

    /**
     * Get current season dynamically.
     */
    private String getCurrentSeason() {
        return com.app.footballprediction.util.SeasonUtils.getCurrentSeason();
    }

    /**
     * Get upcoming matches grouped by match day.
     * Uses external API for live match data.
     */
    @Cacheable(value = CacheConfig.CACHE_MATCHES, key = "'dashboard_upcoming'")
    public UpcomingMatchesResponse getUpcomingMatches() {
        log.debug("Fetching upcoming matches for dashboard from external API");
        long startTime = System.currentTimeMillis();

        try {
            FootballApiResponse apiResponse = footballDataApiService.getScheduledMatches("PL");
            List<FootballApiResponse.ApiMatch> apiMatches = apiResponse.getMatches();

            if (apiMatches == null || apiMatches.isEmpty()) {
                log.debug("No upcoming matches found from external API");
                return buildEmptyResponse();
            }

            List<UpcomingMatchesResponse.UpcomingMatchDto> matchDtos = apiMatches.stream()
                    .limit(10)
                    .map(this::mapApiMatchToUpcomingMatchDto)
                    .toList();

            log.debug("Fetched {} upcoming matches in {}ms", matchDtos.size(), System.currentTimeMillis() - startTime);

            return UpcomingMatchesResponse.builder()
                    .matchDayHeader("Upcoming Matches")
                    .matchDay(null)
                    .competition("Premier League")
                    .matches(matchDtos)
                    .totalMatches(matchDtos.size())
                    .lastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                    .build();
        } catch (Exception e) {
            log.error("Failed to fetch upcoming matches from external API: {}", e.getMessage());
            return buildEmptyResponse();
        }
    }

    private UpcomingMatchesResponse buildEmptyResponse() {
        return UpcomingMatchesResponse.builder()
                .matchDayHeader("Upcoming Matches")
                .matchDay(null)
                .competition("Premier League")
                .matches(List.of())
                .totalMatches(0)
                .lastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    private UpcomingMatchesResponse.UpcomingMatchDto mapApiMatchToUpcomingMatchDto(FootballApiResponse.ApiMatch apiMatch) {
        LocalDate matchDate = parseUtcDate(apiMatch.getUtcDate());
        String matchTime = parseUtcTime(apiMatch.getUtcDate());

        return UpcomingMatchesResponse.UpcomingMatchDto.builder()
                .matchId(apiMatch.getId())
                .homeTeam(apiMatch.getHomeTeam() != null ? apiMatch.getHomeTeam().getName() : "Unknown")
                .awayTeam(apiMatch.getAwayTeam() != null ? apiMatch.getAwayTeam().getName() : "Unknown")
                .homeTeamLogo(apiMatch.getHomeTeam() != null ? apiMatch.getHomeTeam().getCrest() : null)
                .awayTeamLogo(apiMatch.getAwayTeam() != null ? apiMatch.getAwayTeam().getCrest() : null)
                .matchDate(matchDate)
                .matchTime(matchTime)
                .formattedDate(formatMatchDate(matchDate))
                .venue(null)
                .status(apiMatch.getStatus())
                .matchDay(apiMatch.getMatchday())
                .canPredict(true)
                .build();
    }

    private LocalDate parseUtcDate(String utcDate) {
        if (utcDate == null || utcDate.isEmpty()) return null;
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(utcDate);
            return zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        } catch (Exception e) {
            log.warn("Failed to parse UTC date: {}", utcDate);
            return null;
        }
    }

    private String parseUtcTime(String utcDate) {
        if (utcDate == null || utcDate.isEmpty()) return "TBD";
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(utcDate);
            return zdt.withZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return "TBD";
        }
    }

    private UpcomingMatchesResponse.UpcomingMatchDto mapToUpcomingMatchDto(Match match) {
        return UpcomingMatchesResponse.UpcomingMatchDto.builder()
                .matchId(match.getId())
                .homeTeam(match.getHomeTeam())
                .awayTeam(match.getAwayTeam())
                .matchDate(match.getMatchDate())
                .matchTime("TBD")
                .formattedDate(formatMatchDate(match.getMatchDate()))
                .venue(null)
                .status("SCHEDULED")
                .matchDay(null)
                .canPredict(true)
                .build();
    }

    private String formatMatchDate(LocalDate date) {
        if (date == null) return "TBD";
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "Today";
        if (date.equals(today.plusDays(1))) return "Tomorrow";
        return date.format(DateTimeFormatter.ofPattern("EEE, MMM d"));
    }

    /**
     * Get today's predictions with status.
     */
    @Cacheable(value = CacheConfig.CACHE_PREDICTIONS, key = "'dashboard_todays'")
    public TodaysPredictionsResponse getTodaysPredictions() {
        log.debug("Fetching today's predictions for dashboard");
        long startTime = System.currentTimeMillis();

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Get predictions from today and yesterday
        List<Prediction> recentPredictions = predictionRepository.findAll().stream()
                .filter(p -> p.getMatchDate() != null)
                .filter(p -> !p.getMatchDate().isBefore(yesterday) && !p.getMatchDate().isAfter(today))
                .sorted(Comparator.comparing(Prediction::getMatchDate).reversed())
                .limit(10)
                .toList();

        // Deduplicate by matchId (keep one per match)
        Map<Long, Prediction> uniqueByMatch = new LinkedHashMap<>();
        for (Prediction p : recentPredictions) {
            uniqueByMatch.putIfAbsent(p.getMatchId(), p);
        }

        List<TodaysPredictionsResponse.TodaysPredictionDto> predictionDtos = uniqueByMatch.values().stream()
                .map(this::mapToTodaysPredictionDto)
                .toList();

        int wonCount = (int) predictionDtos.stream().filter(p -> "WON".equals(p.getStatus())).count();
        int lostCount = (int) predictionDtos.stream().filter(p -> "LOST".equals(p.getStatus())).count();
        int pendingCount = (int) predictionDtos.stream().filter(p -> "PENDING".equals(p.getStatus())).count();

        log.debug("Fetched {} today's predictions in {}ms", predictionDtos.size(), System.currentTimeMillis() - startTime);

        return TodaysPredictionsResponse.builder()
                .predictions(predictionDtos)
                .totalCount(predictionDtos.size())
                .pendingCount(pendingCount)
                .wonCount(wonCount)
                .lostCount(lostCount)
                .lastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    private TodaysPredictionsResponse.TodaysPredictionDto mapToTodaysPredictionDto(Prediction prediction) {
        String status = "PENDING";
        if (prediction.getActualResult() != null) {
            status = Boolean.TRUE.equals(prediction.getIsCorrect()) ? "WON" : "LOST";
        }

        String predictedWinner = determinePredictedWinner(prediction);

        return TodaysPredictionsResponse.TodaysPredictionDto.builder()
                .matchId(prediction.getMatchId())
                .homeTeam(prediction.isHome() ? prediction.getTeamName() : prediction.getOpponentName())
                .awayTeam(prediction.isHome() ? prediction.getOpponentName() : prediction.getTeamName())
                .matchDate(prediction.getMatchDate())
                .predictedWinner(predictedWinner)
                .predictedResult(prediction.getPredictedResult())
                .confidence(prediction.getConfidence() * 100)
                .status(status)
                .actualHomeGoals(prediction.getActualHomeGoals())
                .actualAwayGoals(prediction.getActualAwayGoals())
                .build();
    }

    private String determinePredictedWinner(Prediction prediction) {
        if ("DRAW".equals(prediction.getPredictedResult())) return "Draw";
        if ("WIN".equals(prediction.getPredictedResult())) {
            return prediction.isHome() ? "Home Win" : "Away Win";
        }
        return prediction.isHome() ? "Away Win" : "Home Win";
    }

    /**
     * Get top teams by different metrics.
     */
    @Cacheable(value = CacheConfig.CACHE_STANDINGS, key = "'dashboard_top_teams'")
    public TopTeamsResponse getTopTeams() {
        log.debug("Fetching top teams for dashboard");
        long startTime = System.currentTimeMillis();

        String currentSeason = getCurrentSeason();
        List<LeagueStanding> standings = standingRepository
                .findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(DEFAULT_LEAGUE_ID, currentSeason);

        if (standings.isEmpty()) {
            // Try to calculate from matches
            standings = leagueStandingService.calculateStandingsFromMatches(DEFAULT_LEAGUE_ID, currentSeason);
        }

        // Top 5 by points
        List<TopTeamsResponse.TopTeamDto> byPoints = standings.stream()
                .limit(5)
                .map(this::mapToTopTeamDto)
                .toList();

        // Top 5 by goal difference
        List<TopTeamsResponse.TopTeamDto> byGD = standings.stream()
                .sorted(Comparator.comparingInt(LeagueStanding::getGoalDifference).reversed())
                .limit(5)
                .map(this::mapToTopTeamDto)
                .toList();

        // Top 5 by form (calculate form score)
        List<TopTeamsResponse.TopTeamDto> byForm = standings.stream()
                .filter(s -> s.getForm() != null && !s.getForm().isEmpty())
                .sorted((a, b) -> Integer.compare(calculateFormScore(b.getForm()), calculateFormScore(a.getForm())))
                .limit(5)
                .map(this::mapToTopTeamDto)
                .toList();

        log.debug("Fetched top teams in {}ms", System.currentTimeMillis() - startTime);

        return TopTeamsResponse.builder()
                .teamsByPoints(byPoints)
                .teamsByGoalDifference(byGD)
                .teamsByForm(byForm)
                .season(currentSeason)
                .lastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    private TopTeamsResponse.TopTeamDto mapToTopTeamDto(LeagueStanding standing) {
        int played = standing.getPlayed() != null ? standing.getPlayed() : 0;
        int won = standing.getWon() != null ? standing.getWon() : 0;
        double winPct = played > 0 ? (double) won / played * 100 : 0;

        return TopTeamsResponse.TopTeamDto.builder()
                .rank(standing.getPosition() != null ? standing.getPosition() : 0)
                .teamName(standing.getTeamName())
                .points(standing.getPoints() != null ? standing.getPoints() : 0)
                .goalDifference(standing.getGoalDifference() != null ? standing.getGoalDifference() : 0)
                .goalsFor(standing.getGoalsFor() != null ? standing.getGoalsFor() : 0)
                .goalsAgainst(standing.getGoalsAgainst() != null ? standing.getGoalsAgainst() : 0)
                .form(standing.getForm())
                .won(won)
                .drawn(standing.getDrawn() != null ? standing.getDrawn() : 0)
                .lost(standing.getLost() != null ? standing.getLost() : 0)
                .played(played)
                .winPercentage(Math.round(winPct * 10) / 10.0)
                .build();
    }

    private int calculateFormScore(String form) {
        if (form == null || form.isEmpty()) return 0;
        int score = 0;
        for (char c : form.toCharArray()) {
            if (c == 'W') score += 3;
            else if (c == 'D') score += 1;
        }
        return score;
    }

    /**
     * Get model accuracy statistics.
     */
    @Cacheable(value = CacheConfig.CACHE_PREDICTIONS, key = "'dashboard_model_accuracy'")
    public ModelAccuracyResponse getModelAccuracy() {
        log.debug("Fetching model accuracy for dashboard");
        long startTime = System.currentTimeMillis();

        long totalResolved = predictionRepository.countAllResolvedPredictions();
        long totalCorrect = predictionRepository.countAllCorrectPredictions();
        long totalPending = predictionRepository.count() - totalResolved;

        double overallAccuracy = totalResolved > 0 ? (double) totalCorrect / totalResolved * 100 : 0;

        // Get last 10 predictions for recent accuracy
        List<Prediction> recentPredictions = predictionRepository.findAllResolvedPredictions().stream()
                .sorted(Comparator.comparing(Prediction::getMatchDate).reversed())
                .limit(10)
                .toList();

        long last10Correct = recentPredictions.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsCorrect()))
                .count();
        double last10Accuracy = recentPredictions.isEmpty() ? 0 : (double) last10Correct / recentPredictions.size() * 100;

        // Calculate trend
        String trendIndicator = "STABLE";
        double trendChange = 0;
        if (recentPredictions.size() >= 10) {
            // Compare last 10 vs overall
            if (last10Accuracy > overallAccuracy + 5) {
                trendIndicator = "UP";
                trendChange = last10Accuracy - overallAccuracy;
            } else if (last10Accuracy < overallAccuracy - 5) {
                trendIndicator = "DOWN";
                trendChange = last10Accuracy - overallAccuracy;
            }
        }

        // Calculate home/away accuracy
        List<Prediction> allResolved = predictionRepository.findAllResolvedPredictions();

        long homePredictions = allResolved.stream().filter(Prediction::isHome).count();
        long homeCorrect = allResolved.stream().filter(p -> p.isHome() && Boolean.TRUE.equals(p.getIsCorrect())).count();
        double homeAccuracy = homePredictions > 0 ? (double) homeCorrect / homePredictions * 100 : 0;

        long awayPredictions = allResolved.stream().filter(p -> !p.isHome()).count();
        long awayCorrect = allResolved.stream().filter(p -> !p.isHome() && Boolean.TRUE.equals(p.getIsCorrect())).count();
        double awayAccuracy = awayPredictions > 0 ? (double) awayCorrect / awayPredictions * 100 : 0;

        // High confidence accuracy
        long highConfPredictions = allResolved.stream().filter(p -> p.getConfidence() >= 0.6).count();
        long highConfCorrect = allResolved.stream()
                .filter(p -> p.getConfidence() >= 0.6 && Boolean.TRUE.equals(p.getIsCorrect()))
                .count();
        double highConfAccuracy = highConfPredictions > 0 ? (double) highConfCorrect / highConfPredictions * 100 : 0;

        log.debug("Fetched model accuracy in {}ms", System.currentTimeMillis() - startTime);

        return ModelAccuracyResponse.builder()
                .overallAccuracy(Math.round(overallAccuracy * 10) / 10.0)
                .last10Accuracy(Math.round(last10Accuracy * 10) / 10.0)
                .totalPredictions(totalResolved)
                .correctPredictions(totalCorrect)
                .incorrectPredictions(totalResolved - totalCorrect)
                .pendingPredictions(totalPending)
                .homeAccuracy(Math.round(homeAccuracy * 10) / 10.0)
                .awayAccuracy(Math.round(awayAccuracy * 10) / 10.0)
                .highConfidenceAccuracy(Math.round(highConfAccuracy * 10) / 10.0)
                .trendIndicator(trendIndicator)
                .trendChange(Math.round(trendChange * 10) / 10.0)
                .lastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }
}

