package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.MatchFeatures;
import com.app.common.model.Prediction;
import com.app.common.model.Team;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.PredictionRepository;
import com.app.common.repository.TeamRepository;
import com.app.common.service.FeatureEngineeringService;
import com.app.common.util.TeamNameNormalizer;
import com.app.footballprediction.config.CacheConfig;
import com.app.footballprediction.dto.TeamAnalyticsDto;
import com.app.footballprediction.dto.TeamAnalyticsDto.*;
import com.app.footballprediction.dto.external.FootballApiResponse;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.util.SeasonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for aggregating comprehensive team analytics.
 * Implements the aggregator pattern to combine data from multiple sources.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamAnalyticsService {

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final TeamRepository teamRepository;
    private final TeamStatsService teamStatsService;
    private final FootballDataApiService footballDataApiService;
    private final FeatureEngineeringService featureEngineeringService;
    private final ModelTrainingService modelTrainingService;

    private static final int MAX_UPCOMING_MATCHES = 10;
    private static final int MAX_RECENT_RESULTS = 10;

    /**
     * Get comprehensive analytics for a team.
     * Aggregates data from matches, predictions, and calculated statistics.
     */
    @Cacheable(value = "teamAnalytics", key = "#teamName.toLowerCase()")
    public TeamAnalyticsDto getTeamAnalytics(String teamName) {
        log.info("Building analytics for team: {}", teamName);
        long startTime = System.currentTimeMillis();

        try {
            // Resolve team name (handles case-insensitivity and fuzzy matching)
            String resolvedTeamName = resolveTeamName(teamName);
            log.debug("Resolved team name: '{}' -> '{}'", teamName, resolvedTeamName);

            LocalDate today = LocalDate.now();
            LocalDate beforeDate = today.plusDays(1);

            // Fetch all required data
            List<Match> allMatches = matchRepository.findByTeamBeforeDate(resolvedTeamName, beforeDate);
            List<Match> homeMatches = matchRepository.findHomeMatchesByTeamBeforeDate(resolvedTeamName, beforeDate);
            List<Match> awayMatches = matchRepository.findAwayMatchesByTeamBeforeDate(resolvedTeamName, beforeDate);
            List<Prediction> allPredictions = predictionRepository.findByTeamNameIgnoreCaseOrderByMatchDateDesc(resolvedTeamName);
            Optional<Team> teamEntity = teamRepository.findByNameIgnoreCase(resolvedTeamName);

            log.debug("Fetched {} matches, {} predictions for {}", allMatches.size(), allPredictions.size(), resolvedTeamName);

            // Build analytics components
            TeamInfo teamInfo = buildTeamInfo(resolvedTeamName, teamEntity, allMatches);
            List<UpcomingMatch> upcomingMatches = buildUpcomingMatches(resolvedTeamName, allPredictions);
            List<SeasonHistory> seasonHistory = buildSeasonHistory(allMatches, resolvedTeamName);
            ModelAccuracy modelAccuracy = buildModelAccuracy(resolvedTeamName, allPredictions);
            List<PredictionComparison> predictionComparison = buildPredictionComparison(resolvedTeamName, allMatches, allPredictions);
            HomeAwayTrend homeAwayTrend = buildHomeAwayTrend(homeMatches, awayMatches, resolvedTeamName);

            TeamAnalyticsDto analytics = TeamAnalyticsDto.builder()
                    .teamInfo(teamInfo)
                    .upcomingMatches(upcomingMatches)
                    .seasonHistory(seasonHistory)
                    .modelAccuracy(modelAccuracy)
                    .predictionComparison(predictionComparison)
                    .homeAwayTrend(homeAwayTrend)
                    .lastUpdated(LocalDateTime.now().toString())
                    .build();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Built analytics for {} in {}ms", resolvedTeamName, duration);

            return analytics;

        } catch (IllegalArgumentException e) {
            log.warn("Team not found: {} - {}", teamName, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to build analytics for {}: {}", teamName, e.getMessage(), e);
            throw new RuntimeException("Failed to build team analytics: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve team name using smart matching
     */
    private String resolveTeamName(String teamName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be empty");
        }

        String trimmedName = teamName.trim();
        LocalDate beforeDate = LocalDate.now().plusDays(1);

        // Try exact match
        List<Match> exactMatches = matchRepository.findByTeamBeforeDate(trimmedName, beforeDate);
        if (!exactMatches.isEmpty()) {
            return trimmedName;
        }

        // Try case-insensitive match
        List<Match> caseInsensitiveMatches = matchRepository.findByTeamBeforeDateIgnoreCase(trimmedName, beforeDate);
        if (!caseInsensitiveMatches.isEmpty()) {
            Match firstMatch = caseInsensitiveMatches.get(0);
            return firstMatch.getHomeTeam().equalsIgnoreCase(trimmedName)
                    ? firstMatch.getHomeTeam()
                    : firstMatch.getAwayTeam();
        }

        // Try fuzzy match
        List<String> similarTeams = matchRepository.findTeamNamesContaining(trimmedName);
        if (!similarTeams.isEmpty()) {
            return similarTeams.get(0);  // Return best match
        }

        throw new IllegalArgumentException("Team not found: " + trimmedName);
    }

    /**
     * Build team information
     */
    private TeamInfo buildTeamInfo(String teamName, Optional<Team> teamEntity, List<Match> allMatches) {
        String currentSeason = getCurrentSeason();
        int currentSeasonMatches = (int) allMatches.stream()
                .filter(m -> currentSeason.equals(m.getSeason()))
                .count();

        TeamInfo.TeamInfoBuilder builder = TeamInfo.builder()
                .name(teamName)
                .totalMatches(allMatches.size())
                .currentSeason(currentSeason)
                .currentSeasonMatches(currentSeasonMatches);

        teamEntity.ifPresent(team -> {
            builder.id(team.getId())
                   .shortName(team.getShortName())
                   .logoUrl(team.getLogoUrl())
                   .primaryColor(team.getPrimaryColor());
        });

        return builder.build();
    }

    /**
     * Build upcoming matches from predictions and external API
     */
    private List<UpcomingMatch> buildUpcomingMatches(String teamName, List<Prediction> allPredictions) {
        // First, try to get from stored predictions
        List<UpcomingMatch> fromPredictions = allPredictions.stream()
                .filter(p -> !p.isResolved())
                .filter(p -> p.getMatchDate() != null && !p.getMatchDate().isBefore(LocalDate.now()))
                .sorted(Comparator.comparing(Prediction::getMatchDate))
                .limit(MAX_UPCOMING_MATCHES)
                .map(p -> UpcomingMatch.builder()
                        .matchId(p.getMatchId())
                        .matchDate(p.getMatchDate())
                        .opponent(p.getOpponentName())
                        .isHome(p.isHome())
                        .venue(p.isHome() ? "Home" : "Away")
                        .predictedResult(p.getPredictedResult())
                        .confidence(p.getConfidence())
                        .probHomeWin(p.getProbHomeWin())
                        .probDraw(p.getProbDraw())
                        .probAwayWin(p.getProbAwayWin())
                        .build())
                .collect(Collectors.toList());

        // If we have stored predictions, return them
        if (!fromPredictions.isEmpty()) {
            return fromPredictions;
        }

        // Try external API first (Premier League teams)
        List<UpcomingMatch> fromApi = fetchUpcomingFromExternalApi(teamName);
        if (!fromApi.isEmpty()) {
            return fromApi;
        }

        // Fallback: Generate simulated fixtures from historical opponents
        // This helps teams not in the external API (Championship, lower leagues, etc.)
        return generateSimulatedUpcomingFixtures(teamName);
    }

    /**
     * Fetch upcoming fixtures from external API and generate predictions
     */
    private List<UpcomingMatch> fetchUpcomingFromExternalApi(String teamName) {
        try {
            log.debug("Fetching upcoming fixtures from external API for team: {}", teamName);

            // Fetch scheduled matches from football-data.org
            FootballApiResponse scheduledMatches = footballDataApiService.getScheduledMatches("PL");

            if (scheduledMatches == null || scheduledMatches.getMatches() == null) {
                log.debug("No scheduled matches returned from external API");
                return Collections.emptyList();
            }

            // Filter matches for this team
            String normalizedTeamName = normalizeTeamName(teamName);
            List<FootballApiResponse.ApiMatch> teamMatches = scheduledMatches.getMatches().stream()
                    .filter(m -> matchesTeam(m, normalizedTeamName))
                    .sorted(Comparator.comparing(FootballApiResponse.ApiMatch::getUtcDate))
                    .limit(MAX_UPCOMING_MATCHES)
                    .toList();

            if (teamMatches.isEmpty()) {
                log.debug("No upcoming matches found for team {} in Premier League API", teamName);
                return Collections.emptyList();
            }

            // Generate predictions for each match
            List<UpcomingMatch> upcomingMatches = new ArrayList<>();
            for (FootballApiResponse.ApiMatch match : teamMatches) {
                try {
                    UpcomingMatch upcoming = buildUpcomingMatchFromApi(match, teamName);
                    if (upcoming != null) {
                        upcomingMatches.add(upcoming);
                    }
                } catch (Exception e) {
                    log.warn("Failed to build prediction for match {}: {}", match.getId(), e.getMessage());
                }
            }

            log.info("Generated {} upcoming match predictions for {} from external API",
                    upcomingMatches.size(), teamName);
            return upcomingMatches;

        } catch (Exception e) {
            log.warn("Failed to fetch upcoming matches from external API for {}: {}", teamName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Generate simulated upcoming fixtures based on historical opponents.
     * This is a fallback for teams not in the Premier League API (Championship, League One, etc.)
     * Uses the team's most common opponents from their current/recent season to create realistic fixtures.
     */
    private List<UpcomingMatch> generateSimulatedUpcomingFixtures(String teamName) {
        try {
            log.debug("Generating simulated fixtures for team: {} (not in PL API)", teamName);

            // Get the team's recent opponents from the current season
            String currentSeason = getCurrentSeason();
            LocalDate futureDate = LocalDate.now().plusYears(1); // Use future date to get all matches
            List<Match> recentMatches = matchRepository.findByTeamAndSeasonBeforeDate(teamName, currentSeason, futureDate);

            if (recentMatches.isEmpty()) {
                // Try last season if current season has no data
                String lastSeason = getPreviousSeason(currentSeason);
                recentMatches = matchRepository.findByTeamAndSeasonBeforeDate(teamName, lastSeason, futureDate);
            }

            if (recentMatches.isEmpty()) {
                log.debug("No historical matches found for {}, cannot generate fixtures", teamName);
                return Collections.emptyList();
            }

            // Find all unique opponents this team has faced
            Set<String> alreadyPlayedThisSeason = new HashSet<>();

            for (Match match : recentMatches) {
                String opponent = match.getHomeTeam().equalsIgnoreCase(teamName)
                        ? match.getAwayTeam()
                        : match.getHomeTeam();
                alreadyPlayedThisSeason.add(opponent);
            }

            // Find opponents not yet played at home or away (for reverse fixture simulation)
            Set<String> homeOpponentsPlayed = recentMatches.stream()
                    .filter(m -> m.getHomeTeam().equalsIgnoreCase(teamName))
                    .map(Match::getAwayTeam)
                    .collect(Collectors.toSet());

            Set<String> awayOpponentsPlayed = recentMatches.stream()
                    .filter(m -> m.getAwayTeam().equalsIgnoreCase(teamName))
                    .map(Match::getHomeTeam)
                    .collect(Collectors.toSet());

            // Generate reverse fixtures (if played away, next fixture is home, and vice versa)
            List<UpcomingMatch> simulatedMatches = new ArrayList<>();
            LocalDate nextMatchDate = LocalDate.now().plusDays(3); // Start from 3 days from now

            // First, add reverse fixtures
            for (String opponent : awayOpponentsPlayed) {
                if (!homeOpponentsPlayed.contains(opponent) && simulatedMatches.size() < MAX_UPCOMING_MATCHES) {
                    UpcomingMatch fixture = generatePredictedFixture(teamName, opponent, true, nextMatchDate);
                    if (fixture != null) {
                        simulatedMatches.add(fixture);
                        nextMatchDate = nextMatchDate.plusDays(7); // Weekly spacing
                    }
                }
            }

            for (String opponent : homeOpponentsPlayed) {
                if (!awayOpponentsPlayed.contains(opponent) && simulatedMatches.size() < MAX_UPCOMING_MATCHES) {
                    UpcomingMatch fixture = generatePredictedFixture(teamName, opponent, false, nextMatchDate);
                    if (fixture != null) {
                        simulatedMatches.add(fixture);
                        nextMatchDate = nextMatchDate.plusDays(7);
                    }
                }
            }

            // If we still need more fixtures, generate from remaining league opponents
            if (simulatedMatches.size() < MAX_UPCOMING_MATCHES) {
                List<String> allLeagueTeams = findLeagueOpponents(teamName, currentSeason);
                boolean isHome = true;
                for (String opponent : allLeagueTeams) {
                    if (!alreadyPlayedThisSeason.contains(opponent) && simulatedMatches.size() < MAX_UPCOMING_MATCHES) {
                        UpcomingMatch fixture = generatePredictedFixture(teamName, opponent, isHome, nextMatchDate);
                        if (fixture != null) {
                            simulatedMatches.add(fixture);
                            nextMatchDate = nextMatchDate.plusDays(7);
                            isHome = !isHome; // Alternate home/away
                        }
                    }
                }
            }

            if (!simulatedMatches.isEmpty()) {
                log.info("Generated {} simulated upcoming fixtures for {} (non-PL team)",
                        simulatedMatches.size(), teamName);
            }

            return simulatedMatches;

        } catch (Exception e) {
            log.warn("Failed to generate simulated fixtures for {}: {}", teamName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Generate a single predicted fixture
     */
    private UpcomingMatch generatePredictedFixture(String teamName, String opponent, boolean isHome, LocalDate matchDate) {
        try {
            String homeTeam = isHome ? teamName : opponent;
            String awayTeam = isHome ? opponent : teamName;

            double probHomeWin = 0.33;
            double probDraw = 0.34;
            double probAwayWin = 0.33;
            String predictedResult = "DRAW";
            double confidence = 0.33;

            if (modelTrainingService.isModelLoaded()) {
                try {
                    MatchFeatures features = featureEngineeringService.buildFeaturesForPrediction(homeTeam, awayTeam);
                    if (features != null) {
                        double[] probabilities = modelTrainingService.predict(features);
                        if (probabilities != null && probabilities.length == 3) {
                            probHomeWin = probabilities[0];
                            probDraw = probabilities[1];
                            probAwayWin = probabilities[2];

                            if (probHomeWin >= probDraw && probHomeWin >= probAwayWin) {
                                predictedResult = isHome ? "WIN" : "LOSS";
                                confidence = probHomeWin;
                            } else if (probAwayWin >= probHomeWin && probAwayWin >= probDraw) {
                                predictedResult = isHome ? "LOSS" : "WIN";
                                confidence = probAwayWin;
                            } else {
                                predictedResult = "DRAW";
                                confidence = probDraw;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not generate prediction for {} vs {}: {}", homeTeam, awayTeam, e.getMessage());
                }
            }

            return UpcomingMatch.builder()
                    .matchId(null)
                    .matchDate(matchDate)
                    .opponent(opponent)
                    .isHome(isHome)
                    .venue(isHome ? "Home" : "Away")
                    .predictedResult(predictedResult)
                    .confidence(confidence)
                    .probHomeWin(probHomeWin)
                    .probDraw(probDraw)
                    .probAwayWin(probAwayWin)
                    .simulated(true) // Flag to indicate this is a simulated fixture
                    .build();

        } catch (Exception e) {
            log.debug("Failed to generate fixture for {} vs {}: {}", teamName, opponent, e.getMessage());
            return null;
        }
    }

    /**
     * Find all teams in the same league/season as the given team
     */
    private List<String> findLeagueOpponents(String teamName, String season) {
        List<Match> seasonMatches = matchRepository.findBySeasonOrderByMatchDateDesc(season);
        Set<String> teams = new HashSet<>();

        for (Match match : seasonMatches) {
            teams.add(match.getHomeTeam());
            teams.add(match.getAwayTeam());
        }

        teams.remove(teamName);
        return new ArrayList<>(teams);
    }


    /**
     * Get previous season string
     */
    private String getPreviousSeason(String currentSeason) {
        try {
            String[] parts = currentSeason.split("-");
            int startYear = Integer.parseInt(parts[0]) - 1;
            int endYear = Integer.parseInt(parts[1]) - 1;
            return startYear + "-" + String.format("%02d", endYear);
        } catch (Exception e) {
            return "2024-25"; // Fallback
        }
    }

    /**
     * Build an UpcomingMatch with prediction from API match data
     */
    private UpcomingMatch buildUpcomingMatchFromApi(FootballApiResponse.ApiMatch apiMatch, String teamName) {
        String homeTeam = apiMatch.getHomeTeam().getName();
        String awayTeam = apiMatch.getAwayTeam().getName();

        // Normalize API team names to match our database format
        String normalizedHomeTeam = TeamNameNormalizer.normalize(homeTeam);
        String normalizedAwayTeam = TeamNameNormalizer.normalize(awayTeam);

        // Determine if our team is home or away using proper team name comparison
        boolean isHome = TeamNameNormalizer.isSameTeam(homeTeam, teamName);
        String opponent = isHome ? normalizedAwayTeam : normalizedHomeTeam;

        // Use normalized names for prediction
        String predictionHomeTeam = isHome ? teamName : normalizedHomeTeam;
        String predictionAwayTeam = isHome ? normalizedAwayTeam : teamName;

        // Parse match date
        LocalDate matchDate = parseApiDate(apiMatch.getUtcDate());
        if (matchDate == null) {
            return null;
        }

        // Try to generate prediction using the model
        double probHomeWin = 0.33;
        double probDraw = 0.34;
        double probAwayWin = 0.33;
        String predictedResult = "DRAW";
        double confidence = 0.33;

        if (modelTrainingService.isModelLoaded()) {
            try {
                // Use normalized team names for prediction (matches database format)
                MatchFeatures features = featureEngineeringService.buildFeaturesForPrediction(
                        predictionHomeTeam, predictionAwayTeam);

                if (features != null) {
                    double[] probabilities = modelTrainingService.predict(features);
                    if (probabilities != null && probabilities.length == 3) {
                        probHomeWin = probabilities[0];
                        probDraw = probabilities[1];
                        probAwayWin = probabilities[2];

                        // Determine predicted result and confidence
                        if (probHomeWin >= probDraw && probHomeWin >= probAwayWin) {
                            predictedResult = isHome ? "WIN" : "LOSS";
                            confidence = probHomeWin;
                        } else if (probAwayWin >= probHomeWin && probAwayWin >= probDraw) {
                            predictedResult = isHome ? "LOSS" : "WIN";
                            confidence = probAwayWin;
                        } else {
                            predictedResult = "DRAW";
                            confidence = probDraw;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Could not generate prediction for {} vs {}: {}", predictionHomeTeam, predictionAwayTeam, e.getMessage());
            }
        }

        return UpcomingMatch.builder()
                .matchId(apiMatch.getId())
                .matchDate(matchDate)
                .opponent(opponent)
                .isHome(isHome)
                .venue(isHome ? "Home" : "Away")
                .predictedResult(predictedResult)
                .confidence(confidence)
                .probHomeWin(probHomeWin)
                .probDraw(probDraw)
                .probAwayWin(probAwayWin)
                .build();
    }

    /**
     * Check if an API match involves the given team.
     * Uses TeamNameNormalizer.isSameTeam() for proper name matching
     * (e.g., "Manchester United FC" matches "Man United").
     */
    private boolean matchesTeam(FootballApiResponse.ApiMatch match, String teamName) {
        if (match.getHomeTeam() == null || match.getAwayTeam() == null) {
            return false;
        }
        String homeTeamName = match.getHomeTeam().getName();
        String awayTeamName = match.getAwayTeam().getName();

        return TeamNameNormalizer.isSameTeam(homeTeamName, teamName) ||
               TeamNameNormalizer.isSameTeam(awayTeamName, teamName);
    }

    /**
     * Normalize team name for matching (handles variations like "Arsenal FC" vs "Arsenal").
     * Uses TeamNameNormalizer for consistent name resolution.
     */
    private String normalizeTeamName(String teamName) {
        if (teamName == null) return "";
        return TeamNameNormalizer.normalize(teamName);
    }

    /**
     * Parse API date string to LocalDate
     */
    private LocalDate parseApiDate(String utcDate) {
        if (utcDate == null || utcDate.isEmpty()) {
            return null;
        }
        try {
            // Format: 2026-02-22T15:00:00Z
            return LocalDate.parse(utcDate.substring(0, 10));
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", utcDate);
            return null;
        }
    }

    /**
     * Build season-wise historical statistics
     */
    private List<SeasonHistory> buildSeasonHistory(List<Match> allMatches, String teamName) {
        // Group matches by season
        Map<String, List<Match>> matchesBySeason = allMatches.stream()
                .filter(m -> m.getSeason() != null && !m.getSeason().isEmpty())
                .collect(Collectors.groupingBy(Match::getSeason));

        return matchesBySeason.entrySet().stream()
                .map(entry -> buildSeasonStats(entry.getKey(), entry.getValue(), teamName))
                .sorted((a, b) -> b.getSeason().compareTo(a.getSeason()))  // Most recent first
                .collect(Collectors.toList());
    }

    /**
     * Build statistics for a single season
     */
    private SeasonHistory buildSeasonStats(String season, List<Match> matches, String teamName) {
        int wins = 0, draws = 0, losses = 0;
        int goalsScored = 0, goalsConceded = 0;
        int homeWins = 0, awayWins = 0, cleanSheets = 0;

        for (Match match : matches) {
            int scored = match.getGoalsScoredByTeam(teamName);
            int conceded = match.getGoalsConcededByTeam(teamName);
            int points = match.getPointsForTeam(teamName);

            goalsScored += scored;
            goalsConceded += conceded;

            if (conceded == 0) cleanSheets++;

            boolean isHome = match.getHomeTeam().equalsIgnoreCase(teamName);

            if (points == 3) {
                wins++;
                if (isHome) homeWins++;
                else awayWins++;
            } else if (points == 1) {
                draws++;
            } else {
                losses++;
            }
        }

        int totalMatches = matches.size();
        double winRate = totalMatches > 0 ? (double) wins / totalMatches * 100 : 0;
        double avgGoalsScored = totalMatches > 0 ? (double) goalsScored / totalMatches : 0;
        double avgGoalsConceded = totalMatches > 0 ? (double) goalsConceded / totalMatches : 0;

        return SeasonHistory.builder()
                .season(season)
                .matchesPlayed(totalMatches)
                .wins(wins)
                .draws(draws)
                .losses(losses)
                .goalsScored(goalsScored)
                .goalsConceded(goalsConceded)
                .goalDifference(goalsScored - goalsConceded)
                .points(wins * 3 + draws)
                .winRate(Math.round(winRate * 100.0) / 100.0)
                .avgGoalsScored(Math.round(avgGoalsScored * 100.0) / 100.0)
                .avgGoalsConceded(Math.round(avgGoalsConceded * 100.0) / 100.0)
                .homeWins(homeWins)
                .awayWins(awayWins)
                .cleanSheets(cleanSheets)
                .build();
    }

    /**
     * Build model accuracy metrics
     */
    private ModelAccuracy buildModelAccuracy(String teamName, List<Prediction> allPredictions) {
        List<Prediction> resolvedPredictions = allPredictions.stream()
                .filter(Prediction::isResolved)
                .collect(Collectors.toList());

        if (resolvedPredictions.isEmpty()) {
            return ModelAccuracy.builder()
                    .totalPredictions(0)
                    .correctPredictions(0)
                    .overallAccuracy(0.0)
                    .accuracyTrend("UNKNOWN")
                    .build();
        }

        // Overall accuracy
        int total = resolvedPredictions.size();
        int correct = (int) resolvedPredictions.stream().filter(p -> Boolean.TRUE.equals(p.getIsCorrect())).count();
        double overallAccuracy = (double) correct / total * 100;

        // High confidence accuracy
        List<Prediction> highConfidence = resolvedPredictions.stream()
                .filter(Prediction::isHighConfidence)
                .collect(Collectors.toList());
        int highConfTotal = highConfidence.size();
        int highConfCorrect = (int) highConfidence.stream().filter(p -> Boolean.TRUE.equals(p.getIsCorrect())).count();
        double highConfAccuracy = highConfTotal > 0 ? (double) highConfCorrect / highConfTotal * 100 : 0;

        // Home accuracy
        List<Prediction> homePreds = resolvedPredictions.stream().filter(Prediction::isHome).collect(Collectors.toList());
        int homeTotal = homePreds.size();
        int homeCorrect = (int) homePreds.stream().filter(p -> Boolean.TRUE.equals(p.getIsCorrect())).count();
        double homeAccuracy = homeTotal > 0 ? (double) homeCorrect / homeTotal * 100 : 0;

        // Away accuracy
        List<Prediction> awayPreds = resolvedPredictions.stream().filter(p -> !p.isHome()).collect(Collectors.toList());
        int awayTotal = awayPreds.size();
        int awayCorrect = (int) awayPreds.stream().filter(p -> Boolean.TRUE.equals(p.getIsCorrect())).count();
        double awayAccuracy = awayTotal > 0 ? (double) awayCorrect / awayTotal * 100 : 0;

        // Average confidence
        double avgConfidence = resolvedPredictions.stream()
                .mapToDouble(Prediction::getConfidence)
                .average()
                .orElse(0.0) * 100;

        // Accuracy by result type
        AccuracyByResult accuracyByResult = buildAccuracyByResult(resolvedPredictions);

        // Determine accuracy trend
        String trend = determineAccuracyTrend(resolvedPredictions);

        return ModelAccuracy.builder()
                .totalPredictions(total)
                .correctPredictions(correct)
                .overallAccuracy(Math.round(overallAccuracy * 100.0) / 100.0)
                .highConfidencePredictions(highConfTotal)
                .correctHighConfidencePredictions(highConfCorrect)
                .highConfidenceAccuracy(Math.round(highConfAccuracy * 100.0) / 100.0)
                .homePredictions(homeTotal)
                .correctHomePredictions(homeCorrect)
                .homeAccuracy(Math.round(homeAccuracy * 100.0) / 100.0)
                .awayPredictions(awayTotal)
                .correctAwayPredictions(awayCorrect)
                .awayAccuracy(Math.round(awayAccuracy * 100.0) / 100.0)
                .averageConfidence(Math.round(avgConfidence * 100.0) / 100.0)
                .accuracyByResult(accuracyByResult)
                .accuracyTrend(trend)
                .build();
    }

    /**
     * Build accuracy breakdown by predicted result type
     */
    private AccuracyByResult buildAccuracyByResult(List<Prediction> predictions) {
        Map<String, List<Prediction>> byResult = predictions.stream()
                .collect(Collectors.groupingBy(p -> p.getPredictedResult() != null ? p.getPredictedResult() : "UNKNOWN"));

        List<Prediction> winPreds = byResult.getOrDefault("WIN", List.of());
        List<Prediction> drawPreds = byResult.getOrDefault("DRAW", List.of());
        List<Prediction> lossPreds = byResult.getOrDefault("LOSS", List.of());

        return AccuracyByResult.builder()
                .winPredictions(winPreds.size())
                .correctWinPredictions((int) winPreds.stream().filter(p -> Boolean.TRUE.equals(p.getIsCorrect())).count())
                .winPredictionAccuracy(calculateAccuracy(winPreds))
                .drawPredictions(drawPreds.size())
                .correctDrawPredictions((int) drawPreds.stream().filter(p -> Boolean.TRUE.equals(p.getIsCorrect())).count())
                .drawPredictionAccuracy(calculateAccuracy(drawPreds))
                .lossPredictions(lossPreds.size())
                .correctLossPredictions((int) lossPreds.stream().filter(p -> Boolean.TRUE.equals(p.getIsCorrect())).count())
                .lossPredictionAccuracy(calculateAccuracy(lossPreds))
                .build();
    }

    private double calculateAccuracy(List<Prediction> predictions) {
        if (predictions.isEmpty()) return 0.0;
        long correct = predictions.stream().filter(p -> Boolean.TRUE.equals(p.getIsCorrect())).count();
        return Math.round((double) correct / predictions.size() * 10000.0) / 100.0;
    }

    /**
     * Determine accuracy trend based on recent predictions
     */
    private String determineAccuracyTrend(List<Prediction> predictions) {
        if (predictions.size() < 10) return "INSUFFICIENT_DATA";

        // Sort by date descending
        List<Prediction> sorted = predictions.stream()
                .sorted(Comparator.comparing(Prediction::getMatchDate).reversed())
                .collect(Collectors.toList());

        // Compare recent vs older accuracy
        int halfPoint = Math.min(sorted.size() / 2, 20);
        List<Prediction> recent = sorted.subList(0, halfPoint);
        List<Prediction> older = sorted.subList(halfPoint, Math.min(halfPoint * 2, sorted.size()));

        double recentAccuracy = calculateAccuracy(recent);
        double olderAccuracy = calculateAccuracy(older);

        double diff = recentAccuracy - olderAccuracy;
        if (diff > 5) return "IMPROVING";
        if (diff < -5) return "DECLINING";
        return "STABLE";
    }

    /**
     * Build prediction vs actual comparison by season
     */
    private List<PredictionComparison> buildPredictionComparison(String teamName, List<Match> allMatches,
                                                                  List<Prediction> allPredictions) {
        // Get distinct seasons from predictions
        Set<String> seasons = allPredictions.stream()
                .filter(p -> p.getSeason() != null && p.isResolved())
                .map(Prediction::getSeason)
                .collect(Collectors.toSet());

        return seasons.stream()
                .map(season -> buildSeasonComparison(season, teamName, allMatches, allPredictions))
                .filter(Objects::nonNull)
                .sorted((a, b) -> b.getSeason().compareTo(a.getSeason()))
                .collect(Collectors.toList());
    }

    private PredictionComparison buildSeasonComparison(String season, String teamName,
                                                        List<Match> allMatches, List<Prediction> allPredictions) {
        List<Prediction> seasonPreds = allPredictions.stream()
                .filter(p -> season.equals(p.getSeason()) && p.isResolved())
                .collect(Collectors.toList());

        if (seasonPreds.isEmpty()) return null;

        // Count predictions by type
        int predictedWins = (int) seasonPreds.stream().filter(p -> "WIN".equals(p.getPredictedResult())).count();
        int predictedDraws = (int) seasonPreds.stream().filter(p -> "DRAW".equals(p.getPredictedResult())).count();
        int predictedLosses = (int) seasonPreds.stream().filter(p -> "LOSS".equals(p.getPredictedResult())).count();

        // Count actual results
        int actualWins = (int) seasonPreds.stream().filter(p -> "WIN".equals(p.getActualResult())).count();
        int actualDraws = (int) seasonPreds.stream().filter(p -> "DRAW".equals(p.getActualResult())).count();
        int actualLosses = (int) seasonPreds.stream().filter(p -> "LOSS".equals(p.getActualResult())).count();

        // Calculate points
        int predictedPoints = predictedWins * 3 + predictedDraws;
        int actualPoints = actualWins * 3 + actualDraws;

        // Calculate goals (from matches)
        List<Match> seasonMatches = allMatches.stream()
                .filter(m -> season.equals(m.getSeason()))
                .collect(Collectors.toList());

        int totalGoals = seasonMatches.stream().mapToInt(m -> m.getGoalsScoredByTeam(teamName)).sum();
        double actualGoalsPerGame = seasonMatches.isEmpty() ? 0 : (double) totalGoals / seasonMatches.size();

        // Accuracy for this season
        int correct = (int) seasonPreds.stream().filter(p -> Boolean.TRUE.equals(p.getIsCorrect())).count();
        double seasonAccuracy = (double) correct / seasonPreds.size() * 100;

        return PredictionComparison.builder()
                .season(season)
                .predictedWins(predictedWins)
                .actualWins(actualWins)
                .predictedDraws(predictedDraws)
                .actualDraws(actualDraws)
                .predictedLosses(predictedLosses)
                .actualLosses(actualLosses)
                .predictedPoints(predictedPoints)
                .actualPoints(actualPoints)
                .actualGoalsPerGame(Math.round(actualGoalsPerGame * 100.0) / 100.0)
                .totalMatches(seasonPreds.size())
                .correctPredictions(correct)
                .seasonAccuracy(Math.round(seasonAccuracy * 100.0) / 100.0)
                .build();
    }

    /**
     * Build home vs away performance trends
     */
    private HomeAwayTrend buildHomeAwayTrend(List<Match> homeMatches, List<Match> awayMatches, String teamName) {
        HomeTrend homeTrend = buildHomeTrend(homeMatches, teamName);
        AwayTrend awayTrend = buildAwayTrend(awayMatches, teamName);

        // Determine stronger venue
        String strongerVenue;
        double homeAdvantage = 0;

        if (homeTrend.getTotalMatches() > 0 && awayTrend.getTotalMatches() > 0) {
            homeAdvantage = homeTrend.getWinRate() - awayTrend.getWinRate();
            if (homeAdvantage > 10) {
                strongerVenue = "HOME";
            } else if (homeAdvantage < -10) {
                strongerVenue = "AWAY";
            } else {
                strongerVenue = "BALANCED";
            }
        } else {
            strongerVenue = "INSUFFICIENT_DATA";
        }

        return HomeAwayTrend.builder()
                .homeTrend(homeTrend)
                .awayTrend(awayTrend)
                .strongerVenue(strongerVenue)
                .homeAdvantage(Math.round(homeAdvantage * 100.0) / 100.0)
                .build();
    }

    private HomeTrend buildHomeTrend(List<Match> homeMatches, String teamName) {
        if (homeMatches.isEmpty()) {
            return HomeTrend.builder()
                    .totalMatches(0)
                    .recentResults(List.of())
                    .build();
        }

        int wins = 0, draws = 0, losses = 0;
        int goalsScored = 0, goalsConceded = 0, cleanSheets = 0;

        for (Match match : homeMatches) {
            int scored = match.getFullTimeHomeGoals();
            int conceded = match.getFullTimeAwayGoals();
            goalsScored += scored;
            goalsConceded += conceded;

            if (conceded == 0) cleanSheets++;

            String result = match.getFullTimeResult();
            if ("H".equals(result)) wins++;
            else if ("D".equals(result)) draws++;
            else losses++;
        }

        int total = homeMatches.size();
        double winRate = (double) wins / total * 100;
        double avgScored = (double) goalsScored / total;
        double avgConceded = (double) goalsConceded / total;
        double cleanSheetRate = (double) cleanSheets / total * 100;

        // Recent results
        List<RecentResult> recentResults = homeMatches.stream()
                .sorted(Comparator.comparing(Match::getMatchDate).reversed())
                .limit(MAX_RECENT_RESULTS)
                .map(m -> RecentResult.builder()
                        .date(m.getMatchDate())
                        .opponent(m.getAwayTeam())
                        .goalsScored(m.getFullTimeHomeGoals())
                        .goalsConceded(m.getFullTimeAwayGoals())
                        .result(resultToLetter(m.getFullTimeResult(), true))
                        .isHome(true)
                        .build())
                .collect(Collectors.toList());

        // Current streak
        int[] streakInfo = calculateStreak(homeMatches, true);

        return HomeTrend.builder()
                .totalMatches(total)
                .wins(wins)
                .draws(draws)
                .losses(losses)
                .winRate(Math.round(winRate * 100.0) / 100.0)
                .goalsScored(goalsScored)
                .goalsConceded(goalsConceded)
                .avgGoalsScored(Math.round(avgScored * 100.0) / 100.0)
                .avgGoalsConceded(Math.round(avgConceded * 100.0) / 100.0)
                .cleanSheets(cleanSheets)
                .cleanSheetRate(Math.round(cleanSheetRate * 100.0) / 100.0)
                .recentResults(recentResults)
                .currentStreak(streakInfo[0])
                .streakType(streakTypeFromCode(streakInfo[1]))
                .build();
    }

    private AwayTrend buildAwayTrend(List<Match> awayMatches, String teamName) {
        if (awayMatches.isEmpty()) {
            return AwayTrend.builder()
                    .totalMatches(0)
                    .recentResults(List.of())
                    .build();
        }

        int wins = 0, draws = 0, losses = 0;
        int goalsScored = 0, goalsConceded = 0, cleanSheets = 0;

        for (Match match : awayMatches) {
            int scored = match.getFullTimeAwayGoals();
            int conceded = match.getFullTimeHomeGoals();
            goalsScored += scored;
            goalsConceded += conceded;

            if (conceded == 0) cleanSheets++;

            String result = match.getFullTimeResult();
            if ("A".equals(result)) wins++;
            else if ("D".equals(result)) draws++;
            else losses++;
        }

        int total = awayMatches.size();
        double winRate = (double) wins / total * 100;
        double avgScored = (double) goalsScored / total;
        double avgConceded = (double) goalsConceded / total;
        double cleanSheetRate = (double) cleanSheets / total * 100;

        // Recent results
        List<RecentResult> recentResults = awayMatches.stream()
                .sorted(Comparator.comparing(Match::getMatchDate).reversed())
                .limit(MAX_RECENT_RESULTS)
                .map(m -> RecentResult.builder()
                        .date(m.getMatchDate())
                        .opponent(m.getHomeTeam())
                        .goalsScored(m.getFullTimeAwayGoals())
                        .goalsConceded(m.getFullTimeHomeGoals())
                        .result(resultToLetter(m.getFullTimeResult(), false))
                        .isHome(false)
                        .build())
                .collect(Collectors.toList());

        // Current streak
        int[] streakInfo = calculateStreak(awayMatches, false);

        return AwayTrend.builder()
                .totalMatches(total)
                .wins(wins)
                .draws(draws)
                .losses(losses)
                .winRate(Math.round(winRate * 100.0) / 100.0)
                .goalsScored(goalsScored)
                .goalsConceded(goalsConceded)
                .avgGoalsScored(Math.round(avgScored * 100.0) / 100.0)
                .avgGoalsConceded(Math.round(avgConceded * 100.0) / 100.0)
                .cleanSheets(cleanSheets)
                .cleanSheetRate(Math.round(cleanSheetRate * 100.0) / 100.0)
                .recentResults(recentResults)
                .currentStreak(streakInfo[0])
                .streakType(streakTypeFromCode(streakInfo[1]))
                .build();
    }

    /**
     * Convert match result to W/D/L letter
     */
    private String resultToLetter(String result, boolean isHome) {
        if (result == null) return "?";
        if ("D".equals(result)) return "D";
        if (isHome) {
            return "H".equals(result) ? "W" : "L";
        } else {
            return "A".equals(result) ? "W" : "L";
        }
    }

    /**
     * Calculate current streak
     * Returns [streakCount, streakType]
     * streakType: 1=WIN, 2=DRAW, 3=LOSS, 4=UNBEATEN, 5=WINLESS
     */
    private int[] calculateStreak(List<Match> matches, boolean isHome) {
        if (matches.isEmpty()) return new int[]{0, 0};

        List<Match> sorted = matches.stream()
                .sorted(Comparator.comparing(Match::getMatchDate).reversed())
                .collect(Collectors.toList());

        String firstResult = sorted.get(0).getFullTimeResult();
        String firstLetter = isHome ?
                ("H".equals(firstResult) ? "W" : "D".equals(firstResult) ? "D" : "L") :
                ("A".equals(firstResult) ? "W" : "D".equals(firstResult) ? "D" : "L");

        int streak = 1;
        int streakType;

        if ("W".equals(firstLetter)) streakType = 1;
        else if ("D".equals(firstLetter)) streakType = 2;
        else streakType = 3;

        for (int i = 1; i < sorted.size(); i++) {
            String result = sorted.get(i).getFullTimeResult();
            String letter = isHome ?
                    ("H".equals(result) ? "W" : "D".equals(result) ? "D" : "L") :
                    ("A".equals(result) ? "W" : "D".equals(result) ? "D" : "L");

            if (letter.equals(firstLetter)) {
                streak++;
            } else {
                break;
            }
        }

        return new int[]{streak, streakType};
    }

    private String streakTypeFromCode(int code) {
        return switch (code) {
            case 1 -> "WIN";
            case 2 -> "DRAW";
            case 3 -> "LOSS";
            case 4 -> "UNBEATEN";
            case 5 -> "WINLESS";
            default -> "NONE";
        };
    }

    /**
     * Get current season identifier
     */
    private String getCurrentSeason() {
        return SeasonUtils.getCurrentSeason();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Cache Management
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clear analytics cache for a specific team.
     * Use when prediction results are updated.
     */
    @CacheEvict(value = CacheConfig.CACHE_TEAM_ANALYTICS, key = "#teamName.toLowerCase()")
    public void evictTeamAnalyticsCache(String teamName) {
        log.info("Evicted analytics cache for team: {}", teamName);
    }

    /**
     * Clear all team analytics caches.
     * Use after bulk data updates or model retraining.
     */
    @CacheEvict(value = CacheConfig.CACHE_TEAM_ANALYTICS, allEntries = true)
    public void evictAllAnalyticsCache() {
        log.info("Evicted all team analytics cache");
    }
}

