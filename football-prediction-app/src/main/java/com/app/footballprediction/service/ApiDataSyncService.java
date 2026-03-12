package com.app.footballprediction.service;

import com.app.common.model.League;
import com.app.common.model.LeagueStanding;
import com.app.common.model.Match;
import com.app.common.repository.LeagueRepository;
import com.app.common.repository.LeagueStandingRepository;
import com.app.common.repository.MatchRepository;
import com.app.common.util.SeasonHelper;
import com.app.footballprediction.dto.external.FootballApiResponse;
import com.app.footballprediction.dto.external.StandingsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Service for synchronizing data from football-data.org API to the local database.
 *
 * <p>Mapping between API DTOs and entities is handled by {@link MatchMapper}.
 * Cache invalidation is delegated to {@link CacheOrchestrationService}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiDataSyncService {

    private final FootballDataApiService apiService;
    private final LeagueStandingRepository standingRepository;
    private final MatchRepository matchRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueStandingService standingService;
    private final CacheOrchestrationService cacheOrchestration;

    // ══════════════════════════════════════════════════════════════
    // Main Sync Methods
    // ══════════════════════════════════════════════════════════════

    /**
     * Sync standings from football-data.org API to local database.
     * Deletes existing standings for the season and inserts fresh data.
     *
     * @param competitionCode Competition code (e.g., "PL" for Premier League)
     * @return Number of teams synced
     */
    @Transactional
    public int syncStandings(String competitionCode) {
        log.info("🔄 Syncing standings for competition: {}", competitionCode);

        try {
            // Fetch standings from API
            StandingsResponse response = apiService.getStandings(competitionCode);

            if (response == null || response.getStandings() == null || response.getStandings().isEmpty()) {
                log.warn("❌ No standings data returned from API for: {}", competitionCode);
                return 0;
            }

            // Get or create league
            League league = getOrCreateLeague(competitionCode, response);

            // Extract current season from API response (normalized format)
            String season = extractSeason(response);

            // Update league's current season if different
            if (!season.equals(league.getCurrentSeason())) {
                league.setCurrentSeason(season);
                leagueRepository.save(league);
                log.info("Updated league {} current season to: {}", competitionCode, season);
            }

            // Delete existing standings for this league and season (both formats)
            standingRepository.deleteByLeagueIdAndSeason(league.getId(), season);
            standingRepository.deleteByLeagueIdAndSeason(league.getId(), season.replace("-", "/"));
            log.debug("Deleted existing standings for league {} season {}", league.getId(), season);

            // Find TOTAL standings (not HOME or AWAY specific)
            StandingsResponse.StandingType totalStandings = response.getStandings().stream()
                    .filter(s -> "TOTAL".equals(s.getType()))
                    .findFirst()
                    .orElse(null);

            if (totalStandings == null || totalStandings.getTable() == null) {
                log.warn("❌ No TOTAL standings found in API response");
                return 0;
            }

            int syncedCount = 0;

            // Loop through each table entry and create standings
            for (StandingsResponse.TableEntry entry : totalStandings.getTable()) {
                String teamName = apiService.normalizeTeamName(entry.getTeam().getName());

                // Get form from API - if null, calculate from recent matches
                String form = normalizeForm(entry.getForm());
                if (form == null || form.isEmpty()) {
                    form = calculateFormFromMatches(teamName, 5);
                    if (form != null && !form.isEmpty()) {
                        log.debug("Calculated form for {} from match history: {}", teamName, form);
                    }
                }

                LeagueStanding standing = LeagueStanding.builder()
                        .leagueId(league.getId())
                        .season(season)
                        .teamName(teamName)
                        .position(entry.getPosition())
                        .played(entry.getPlayedGames())
                        .won(entry.getWon())
                        .drawn(entry.getDraw())
                        .lost(entry.getLost())
                        .goalsFor(entry.getGoalsFor())
                        .goalsAgainst(entry.getGoalsAgainst())
                        .goalDifference(entry.getGoalDifference())
                        .points(entry.getPoints())
                        .form(form)
                        .lastUpdated(LocalDateTime.now())
                        .build();

                standingRepository.save(standing);
                syncedCount++;
            }

            // Clear standings cache
            standingService.clearStandingsCache();
            apiService.clearStandingsCache(competitionCode);

            log.info("✅ Synced {} teams to standings table for {} (season: {})", syncedCount, competitionCode, season);
            return syncedCount;

        } catch (Exception e) {
            log.error("❌ Failed to sync standings for {}: {}", competitionCode, e.getMessage(), e);
            throw new RuntimeException("Failed to sync standings: " + e.getMessage(), e);
        }
    }

    /**
     * Sync finished matches from football-data.org API to local database.
     * Updates existing matches or creates new ones.
     *
     * @param competitionCode Competition code (e.g., "PL")
     * @return Array with [newMatches, updatedMatches]
     */
    @Transactional
    public int[] syncFinishedMatches(String competitionCode) {
        log.info("🔄 Syncing finished matches for competition: {}", competitionCode);

        int newMatches = 0;
        int updatedMatches = 0;

        try {
            // Fetch finished matches from API
            FootballApiResponse response = apiService.getFinishedMatches(competitionCode);

            if (response == null || response.getMatches() == null || response.getMatches().isEmpty()) {
                log.warn("❌ No finished matches returned from API for: {}", competitionCode);
                return new int[]{0, 0};
            }

            for (FootballApiResponse.ApiMatch apiMatch : response.getMatches()) {
                // Skip non-finished matches
                if (!"FINISHED".equals(apiMatch.getStatus())) {
                    continue;
                }

                // Normalize team names
                String homeTeam = apiService.normalizeTeamName(apiMatch.getHomeTeam().getName());
                String awayTeam = apiService.normalizeTeamName(apiMatch.getAwayTeam().getName());

                // Extract match date
                LocalDate matchDate = MatchMapper.extractMatchDate(apiMatch.getUtcDate());

                // Check if match already exists
                Match existingMatch = matchRepository.findByMatchDateAndHomeTeamAndAwayTeam(
                        matchDate, homeTeam, awayTeam);

                if (existingMatch == null) {
                    Match newMatch = MatchMapper.fromApi(apiMatch, homeTeam, awayTeam, matchDate);
                    matchRepository.save(newMatch);
                    newMatches++;
                    log.debug("New match saved: {} vs {} on {}", homeTeam, awayTeam, matchDate);
                } else {
                    if (MatchMapper.updateFromApi(existingMatch, apiMatch)) {
                        matchRepository.save(existingMatch);
                        updatedMatches++;
                        log.debug("Match updated: {} vs {} on {}", homeTeam, awayTeam, matchDate);
                    }
                }
            }

            // Clear matches cache
            apiService.clearMatchesCache();

            log.info("✅ Synced finished matches for {}: {} new, {} updated", competitionCode, newMatches, updatedMatches);
            return new int[]{newMatches, updatedMatches};

        } catch (Exception e) {
            log.error("❌ Failed to sync finished matches for {}: {}", competitionCode, e.getMessage(), e);
            throw new RuntimeException("Failed to sync finished matches: " + e.getMessage(), e);
        }
    }

    /**
     * Sync scheduled matches (fixtures) from football-data.org API to local database.
     *
     * @param competitionCode Competition code (e.g., "PL")
     * @return Number of scheduled matches synced
     */
    @Transactional
    public int syncScheduledMatches(String competitionCode) {
        log.info("🔄 Syncing scheduled matches for competition: {}", competitionCode);

        int scheduledCount = 0;

        try {
            // Fetch scheduled matches from API
            FootballApiResponse response = apiService.getScheduledMatches(competitionCode);

            if (response == null || response.getMatches() == null || response.getMatches().isEmpty()) {
                log.info("ℹ️ No scheduled matches returned from API for: {}", competitionCode);
                return 0;
            }

            for (FootballApiResponse.ApiMatch apiMatch : response.getMatches()) {
                // Normalize team names
                String homeTeam = apiService.normalizeTeamName(apiMatch.getHomeTeam().getName());
                String awayTeam = apiService.normalizeTeamName(apiMatch.getAwayTeam().getName());

                // Extract match date
                LocalDate matchDate = MatchMapper.extractMatchDate(apiMatch.getUtcDate());

                // Check if fixture already exists
                boolean exists = matchRepository.existsByMatchDateAndHomeTeamAndAwayTeam(
                        matchDate, homeTeam, awayTeam);

                if (!exists) {
                    // Create fixture (match without scores)
                    Match fixture = Match.builder()
                            .homeTeam(homeTeam)
                            .awayTeam(awayTeam)
                            .matchDate(matchDate)
                            .season(SeasonHelper.deriveSeason(matchDate))
                            .kickoffTime(MatchMapper.extractKickoffTime(apiMatch.getUtcDate()))
                            // No scores set - this is a fixture
                            .fullTimeHomeGoals(null)
                            .fullTimeAwayGoals(null)
                            .fullTimeResult(null)
                            .build();

                    matchRepository.save(fixture);
                    scheduledCount++;
                    log.debug("Fixture saved: {} vs {} on {}", homeTeam, awayTeam, matchDate);
                }
            }

            // Clear matches cache
            apiService.clearMatchesCache();

            log.info("✅ Synced {} scheduled matches for {}", scheduledCount, competitionCode);
            return scheduledCount;

        } catch (Exception e) {
            log.error("❌ Failed to sync scheduled matches for {}: {}", competitionCode, e.getMessage(), e);
            throw new RuntimeException("Failed to sync scheduled matches: " + e.getMessage(), e);
        }
    }

    /**
     * Perform a full data sync: standings + finished matches + scheduled matches.
     *
     * @param competitionCode Competition code (e.g., "PL")
     */
    @Transactional
    public void syncAll(String competitionCode) {
        log.info("🔄 Starting FULL DATA SYNC for: {}", competitionCode);

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Sync standings
            int standingsCount = syncStandings(competitionCode);
            log.info("📊 Standings sync completed: {} teams", standingsCount);

            // Step 2: Sync finished matches
            int[] matchResult = syncFinishedMatches(competitionCode);
            log.info("⚽ Finished matches sync completed: {} new, {} updated", matchResult[0], matchResult[1]);

            // Step 3: Sync scheduled matches
            int scheduledCount = syncScheduledMatches(competitionCode);
            log.info("📅 Scheduled matches sync completed: {} fixtures", scheduledCount);

            // Step 4: Clear all related caches
            cacheOrchestration.clearAllDataCaches();
            log.info("🧹 All related caches cleared");

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Full sync completed successfully for {} in {}ms", competitionCode, duration);

        } catch (Exception e) {
            log.error("❌ Full sync failed for {}: {}", competitionCode, e.getMessage(), e);
            throw new RuntimeException("Full sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Intelligently sync only what's needed based on current database state.
     * This is more efficient than full sync as it only fetches missing data.
     *
     * @param competitionCode Competition code (e.g., "PL")
     */
    @Transactional
    public void smartSync(String competitionCode) {
        log.info("🧠 Starting SMART SYNC for: {}", competitionCode);

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Always sync standings first (small and important)
            int standingsCount = syncStandings(competitionCode);
            log.info("📊 Standings sync completed: {} teams", standingsCount);

            // Step 2: Check what matches we already have
            java.util.List<Match> latestMatches = matchRepository.findAll(
                    org.springframework.data.domain.PageRequest.of(0, 1,
                            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "matchDate"))
            ).getContent();

            if (latestMatches.isEmpty()) {
                // No existing matches - perform full sync
                log.info("📭 No existing matches - performing FULL match sync");
                int[] matchResult = syncFinishedMatches(competitionCode);
                log.info("⚽ Finished matches sync completed: {} new, {} updated", matchResult[0], matchResult[1]);
            } else {
                // Sync only matches since the latest known date
                LocalDate latestDate = latestMatches.get(0).getMatchDate();
                // Use minusDays(1) to ensure we don't miss anything due to timezone issues
                LocalDate syncSinceDate = latestDate.minusDays(1);
                log.info("📅 Latest match in DB: {} - syncing since {}", latestDate, syncSinceDate);

                int[] matchResult = syncMatchesSinceDate(competitionCode, syncSinceDate);
                log.info("⚽ Incremental match sync completed: {} new, {} updated", matchResult[0], matchResult[1]);
            }

            // Step 3: Always sync scheduled matches (fixtures)
            int scheduledCount = syncScheduledMatches(competitionCode);
            log.info("📅 Scheduled matches sync completed: {} fixtures", scheduledCount);

            // Step 4: Clear all related caches
            cacheOrchestration.clearAllDataCaches();
            log.info("🧹 All related caches cleared");

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Smart sync completed successfully for {} in {}ms", competitionCode, duration);

        } catch (Exception e) {
            log.error("❌ Smart sync failed for {}: {}", competitionCode, e.getMessage(), e);
            throw new RuntimeException("Smart sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Sync only matches that occurred on or after a specific date.
     * This is more efficient than syncing all matches.
     *
     * @param competitionCode Competition code (e.g., "PL")
     * @param sinceDate Only sync matches on or after this date
     * @return Array with [newMatches, updatedMatches]
     */
    @Transactional
    public int[] syncMatchesSinceDate(String competitionCode, LocalDate sinceDate) {
        log.info("🔄 Syncing matches since {} for: {}", sinceDate, competitionCode);

        int newMatches = 0;
        int updatedMatches = 0;

        try {
            // Fetch finished matches from API
            FootballApiResponse response = apiService.getFinishedMatches(competitionCode);

            if (response == null || response.getMatches() == null || response.getMatches().isEmpty()) {
                log.info("ℹ️ No finished matches returned from API for: {}", competitionCode);
                return new int[]{0, 0};
            }

            for (FootballApiResponse.ApiMatch apiMatch : response.getMatches()) {
                // Skip non-finished matches
                if (!"FINISHED".equals(apiMatch.getStatus())) {
                    continue;
                }

                // Extract match date
                LocalDate matchDate = MatchMapper.extractMatchDate(apiMatch.getUtcDate());

                // Skip matches before the sinceDate
                if (matchDate.isBefore(sinceDate)) {
                    continue;
                }

                // Normalize team names
                String homeTeam = apiService.normalizeTeamName(apiMatch.getHomeTeam().getName());
                String awayTeam = apiService.normalizeTeamName(apiMatch.getAwayTeam().getName());

                // Check if match already exists
                Match existingMatch = matchRepository.findByMatchDateAndHomeTeamAndAwayTeam(
                        matchDate, homeTeam, awayTeam);

                if (existingMatch == null) {
                    Match newMatch = MatchMapper.fromApi(apiMatch, homeTeam, awayTeam, matchDate);
                    matchRepository.save(newMatch);
                    newMatches++;
                    log.debug("New match saved: {} vs {} on {}", homeTeam, awayTeam, matchDate);
                } else {
                    if (MatchMapper.updateFromApi(existingMatch, apiMatch)) {
                        matchRepository.save(existingMatch);
                        updatedMatches++;
                        log.debug("Match updated: {} vs {} on {}", homeTeam, awayTeam, matchDate);
                    }
                }
            }

            // Clear matches cache
            apiService.clearMatchesCache();

            log.info("✅ Synced matches since {}: {} new, {} updated", sinceDate, newMatches, updatedMatches);
            return new int[]{newMatches, updatedMatches};

        } catch (Exception e) {
            log.error("❌ Failed to sync matches since {} for {}: {}", sinceDate, competitionCode, e.getMessage(), e);
            throw new RuntimeException("Failed to sync matches since " + sinceDate + ": " + e.getMessage(), e);
        }
    }

    /**
     * Normalize all season data in the database to standard format (YYYY-YY with dash).
     * Also recalculates missing form data for standings.
     *
     * This fixes inconsistent season formats like "2025/26" vs "2025-26".
     *
     * @return Map with counts of normalized matches and standings
     */
    @Transactional
    public java.util.Map<String, Integer> normalizeAllSeasonData() {
        log.info("🔧 Starting season data normalization...");

        int matchesNormalized = 0;
        int standingsNormalized = 0;
        int formCalculated = 0;

        try {
            // Step 1: Normalize season format in matches
            java.util.List<Match> allMatches = matchRepository.findAll();
            for (Match match : allMatches) {
                if (match.getSeason() != null) {
                    String normalized = SeasonHelper.normalizeSeason(match.getSeason());
                    if (!normalized.equals(match.getSeason())) {
                        match.setSeason(normalized);
                        matchRepository.save(match);
                        matchesNormalized++;
                    }
                }
            }
            log.info("📊 Normalized {} match seasons", matchesNormalized);

            // Step 2: Normalize season format in standings and calculate missing form
            java.util.List<LeagueStanding> allStandings = standingRepository.findAll();
            for (LeagueStanding standing : allStandings) {
                boolean needsSave = false;

                // Normalize season
                if (standing.getSeason() != null) {
                    String normalized = SeasonHelper.normalizeSeason(standing.getSeason());
                    if (!normalized.equals(standing.getSeason())) {
                        standing.setSeason(normalized);
                        standingsNormalized++;
                        needsSave = true;
                    }
                }

                // Calculate missing form
                if (standing.getForm() == null || standing.getForm().isEmpty()) {
                    String calculatedForm = calculateFormFromMatches(standing.getTeamName(), 5);
                    if (calculatedForm != null && !calculatedForm.isEmpty()) {
                        standing.setForm(calculatedForm);
                        formCalculated++;
                        needsSave = true;
                        log.debug("Calculated form for {}: {}", standing.getTeamName(), calculatedForm);
                    }
                }

                if (needsSave) {
                    standingRepository.save(standing);
                }
            }
            log.info("📊 Normalized {} standing seasons, calculated {} missing forms",
                    standingsNormalized, formCalculated);

            // Step 3: Clear all caches
            cacheOrchestration.clearAllDataCaches();
            log.info("🧹 All caches cleared");

            log.info("✅ Season normalization completed: {} matches, {} standings, {} forms calculated",
                    matchesNormalized, standingsNormalized, formCalculated);

            return java.util.Map.of(
                    "matchesNormalized", matchesNormalized,
                    "standingsNormalized", standingsNormalized,
                    "formsCalculated", formCalculated
            );

        } catch (Exception e) {
            log.error("❌ Failed to normalize season data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to normalize season data: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Helper Methods
    // ══════════════════════════════════════════════════════════════

    /**
     * Extract season from API response and normalize to standard format.
     */
    private String extractSeason(StandingsResponse response) {
        if (response.getSeason() != null && response.getSeason().getStartDate() != null) {
            return SeasonHelper.extractSeasonFromStartDate(response.getSeason().getStartDate());
        }
        return SeasonHelper.currentSeason();
    }

    /**
     * Normalize form string from API format to display format.
     * API: "W,W,D,L,W" → Display: "W W D L W"
     */
    private String normalizeForm(String apiForm) {
        if (apiForm == null || apiForm.isEmpty()) {
            return null;
        }
        return apiForm.replace(",", " ");
    }

    /**
     * Calculate team form from recent match results.
     * Used as fallback when API doesn't provide form data.
     */
    private String calculateFormFromMatches(String teamName, int matchCount) {
        try {
            java.util.List<Match> recentMatches = matchRepository.findAllByOrderByMatchDateDesc()
                    .stream()
                    .filter(m -> m.getFullTimeResult() != null &&
                                (teamName.equalsIgnoreCase(m.getHomeTeam()) || teamName.equalsIgnoreCase(m.getAwayTeam())))
                    .limit(matchCount)
                    .toList();

            if (recentMatches.isEmpty()) {
                return null;
            }

            StringBuilder form = new StringBuilder();
            for (Match match : recentMatches) {
                boolean isHome = teamName.equalsIgnoreCase(match.getHomeTeam());
                String result = match.getFullTimeResult();
                if (result == null) continue;

                String teamResult;
                if ("D".equals(result)) {
                    teamResult = "D";
                } else if ("H".equals(result)) {
                    teamResult = isHome ? "W" : "L";
                } else {
                    teamResult = isHome ? "L" : "W";
                }

                if (form.length() > 0) form.append(" ");
                form.append(teamResult);
            }

            return form.length() > 0 ? form.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to calculate form for team {}: {}", teamName, e.getMessage());
            return null;
        }
    }

    /**
     * Get or create a League entity for the given competition code.
     */
    private League getOrCreateLeague(String competitionCode, StandingsResponse response) {
        return leagueRepository.findByCode(competitionCode)
                .orElseGet(() -> {
                    String leagueName = "Unknown League";
                    if (response.getCompetition() != null && response.getCompetition().getName() != null) {
                        leagueName = response.getCompetition().getName();
                    }

                    League newLeague = League.builder()
                            .code(competitionCode)
                            .name(leagueName)
                            .enabled(true)
                            .displayOrder(100)
                            .currentSeason(extractSeason(response))
                            .build();

                    log.info("Created new league: {} ({})", leagueName, competitionCode);
                    return leagueRepository.save(newLeague);
                });
    }
}

