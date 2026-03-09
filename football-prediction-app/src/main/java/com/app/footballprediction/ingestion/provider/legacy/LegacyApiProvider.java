package com.app.footballprediction.ingestion.provider.legacy;

import com.app.common.ingestion.dto.InternalMatchDto;
import com.app.common.ingestion.dto.InternalStandingDto;
import com.app.common.ingestion.provider.MatchDataProvider;
import com.app.common.ingestion.provider.StandingsDataProvider;
import com.app.footballprediction.dto.external.FootballApiResponse;
import com.app.footballprediction.dto.external.StandingsResponse;
import com.app.footballprediction.service.FootballDataApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Legacy adapter wrapping existing FootballDataApiService.
 * Implements provider interfaces to participate in new architecture
 * while preserving all existing behavior.
 *
 * <p>This adapter:
 * <ul>
 *   <li>Delegates to existing FootballDataApiService</li>
 *   <li>Maps API responses to canonical DTOs</li>
 *   <li>Preserves all existing caching behavior</li>
 *   <li>Does NOT modify existing service logic</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> This adapter only reads data.
 * All actual API calls go through the existing service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyApiProvider implements MatchDataProvider, StandingsDataProvider {

    private static final String PROVIDER_NAME = "FOOTBALL_DATA_ORG";

    private final FootballDataApiService footballDataApiService;

    // ══════════════════════════════════════════════════════════════
    // MatchDataProvider Implementation
    // ══════════════════════════════════════════════════════════════

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public List<InternalMatchDto> getMatchesBySeason(String season) {
        // The external API doesn't have historical seasons
        // This would need to combine finished matches
        log.debug("Legacy API provider - getMatchesBySeason not fully supported");
        return Collections.emptyList();
    }

    @Override
    public List<InternalMatchDto> getRecentCompletedMatches(String competition, int limit) {
        log.debug("Fetching {} recent completed matches for {} from external API", limit, competition);

        try {
            FootballApiResponse response = footballDataApiService.getFinishedMatches(competition);

            if (response == null || response.getMatches() == null) {
                log.debug("No finished matches returned from external API");
                return Collections.emptyList();
            }

            return response.getMatches().stream()
                .sorted(Comparator.comparing(FootballApiResponse.ApiMatch::getUtcDate).reversed())
                .limit(limit)
                .map(this::mapApiMatchToDto)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to fetch finished matches from external API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<InternalMatchDto> getScheduledMatches(String competition) {
        log.debug("Fetching scheduled matches for {} from external API", competition);

        try {
            FootballApiResponse response = footballDataApiService.getScheduledMatches(competition);

            if (response == null || response.getMatches() == null) {
                log.debug("No scheduled matches returned from external API");
                return Collections.emptyList();
            }

            return response.getMatches().stream()
                .map(this::mapApiMatchToDto)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to fetch scheduled matches from external API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<InternalMatchDto> getMatchesByMatchday(String competition, int matchday) {
        log.debug("Fetching matchday {} for {} from external API", matchday, competition);

        try {
            FootballApiResponse response = footballDataApiService.getMatchdayMatches(competition, matchday);

            if (response == null || response.getMatches() == null) {
                return Collections.emptyList();
            }

            return response.getMatches().stream()
                .map(this::mapApiMatchToDto)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to fetch matchday {} from external API: {}", matchday, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isAvailable() {
        // We can't easily check API availability without making a call
        // Return true and let the actual calls handle errors
        return true;
    }

    @Override
    public int getPriority() {
        // High priority for live data
        return 10;
    }

    @Override
    public List<String> getSupportedCompetitions() {
        // football-data.org free tier supports these
        return List.of("PL", "BL1", "SA", "PD", "FL1");
    }

    // ══════════════════════════════════════════════════════════════
    // StandingsDataProvider Implementation
    // ══════════════════════════════════════════════════════════════

    @Override
    public List<InternalStandingDto> getStandings(String competition) {
        log.debug("Fetching standings for {} from external API", competition);

        try {
            StandingsResponse response = footballDataApiService.getStandings(competition);

            if (response == null || response.getStandings() == null) {
                log.debug("No standings returned from external API");
                return Collections.emptyList();
            }

            // Find TOTAL standings (not HOME or AWAY specific)
            return response.getStandings().stream()
                .filter(s -> "TOTAL".equals(s.getType()))
                .findFirst()
                .map(StandingsResponse.StandingType::getTable)
                .orElse(Collections.emptyList())
                .stream()
                .map(entry -> mapTableEntryToDto(entry, competition))
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to fetch standings from external API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public String getTeamForm(String teamName, String competition) {
        List<InternalStandingDto> standings = getStandings(competition);

        return standings.stream()
            .filter(s -> s.getTeamName().equalsIgnoreCase(teamName))
            .findFirst()
            .map(InternalStandingDto::getForm)
            .orElse(null);
    }

    // ══════════════════════════════════════════════════════════════
    // Mapping Methods
    // ══════════════════════════════════════════════════════════════

    /**
     * Map API match response to canonical DTO.
     * Uses existing team name normalization.
     */
    private InternalMatchDto mapApiMatchToDto(FootballApiResponse.ApiMatch apiMatch) {
        String homeTeam = footballDataApiService.normalizeTeamName(apiMatch.getHomeTeam().getName());
        String awayTeam = footballDataApiService.normalizeTeamName(apiMatch.getAwayTeam().getName());

        InternalMatchDto.InternalMatchDtoBuilder builder = InternalMatchDto.builder()
            .externalId(String.valueOf(apiMatch.getId()))
            .providerName(PROVIDER_NAME)
            .homeTeam(homeTeam)
            .awayTeam(awayTeam)
            .matchday(apiMatch.getMatchday())
            .status(apiMatch.getStatus())
            .competition("PL")
            .homeTeamCrest(apiMatch.getHomeTeam().getCrest())
            .awayTeamCrest(apiMatch.getAwayTeam().getCrest())
            .fetchedAt(Instant.now());

        // Parse date and kick-off time
        if (apiMatch.getUtcDate() != null) {
            try {
                LocalDate matchDate = LocalDate.parse(apiMatch.getUtcDate().substring(0, 10));
                builder.matchDate(matchDate);
                builder.season(determineSeason(matchDate));

                // Extract kick-off time (e.g., "2026-02-15T15:00:00Z" → "15:00")
                if (apiMatch.getUtcDate().length() >= 16) {
                    builder.kickoffTime(apiMatch.getUtcDate().substring(11, 16));
                }
            } catch (Exception e) {
                log.warn("Failed to parse date {}: {}", apiMatch.getUtcDate(), e.getMessage());
            }
        }

        // Add score if available
        if (apiMatch.getScore() != null && apiMatch.getScore().getFullTime() != null) {
            FootballApiResponse.ScoreDetail fullTime = apiMatch.getScore().getFullTime();
            builder.fullTimeHomeGoals(fullTime.getHome());
            builder.fullTimeAwayGoals(fullTime.getAway());

            // Determine result
            if (fullTime.getHome() != null && fullTime.getAway() != null) {
                if (fullTime.getHome() > fullTime.getAway()) {
                    builder.fullTimeResult("H");
                } else if (fullTime.getAway() > fullTime.getHome()) {
                    builder.fullTimeResult("A");
                } else {
                    builder.fullTimeResult("D");
                }
            }

            // Half-time score
            if (apiMatch.getScore().getHalfTime() != null) {
                builder.halfTimeHomeGoals(apiMatch.getScore().getHalfTime().getHome());
                builder.halfTimeAwayGoals(apiMatch.getScore().getHalfTime().getAway());
            }
        }

        return builder.build();
    }

    /**
     * Map standings table entry to canonical DTO.
     */
    private InternalStandingDto mapTableEntryToDto(StandingsResponse.TableEntry entry, String competition) {
        String teamName = footballDataApiService.normalizeTeamName(entry.getTeam().getName());

        return InternalStandingDto.builder()
            .teamName(teamName)
            .competition(competition)
            .position(entry.getPosition())
            .played(entry.getPlayedGames())
            .won(entry.getWon())
            .drawn(entry.getDraw())
            .lost(entry.getLost())
            .goalsFor(entry.getGoalsFor())
            .goalsAgainst(entry.getGoalsAgainst())
            .goalDifference(entry.getGoalDifference())
            .points(entry.getPoints())
            .form(entry.getForm())
            .teamCrest(entry.getTeam().getCrest())
            .externalTeamId(String.valueOf(entry.getTeam().getId()))
            .providerName(PROVIDER_NAME)
            .fetchedAt(Instant.now())
            .build();
    }

    /**
     * Determine season from match date.
     * English Premier League season runs August to May.
     */
    private String determineSeason(LocalDate matchDate) {
        int year = matchDate.getYear();
        int month = matchDate.getMonthValue();

        // If month is Aug-Dec, season starts this year
        // If month is Jan-Jul, season started previous year
        int startYear = (month >= 8) ? year : year - 1;
        int endYear = startYear + 1;

        return String.format("%d-%02d", startYear, endYear % 100);
    }
}

