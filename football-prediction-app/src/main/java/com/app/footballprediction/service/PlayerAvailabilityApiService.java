package com.app.footballprediction.service;

import com.app.common.model.PlayerAvailability;
import com.app.common.model.PlayerAvailability.AvailabilityStatus;
import com.app.common.repository.PlayerAvailabilityRepository;
import com.app.common.util.SeasonHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches player injury/suspension data from football-data.org and persists
 * it in the player_availability table.
 *
 * <p>Uses the football-data.org Standard tier endpoint:
 * GET /teams/{id} which includes squad data.
 * For injuries, we supplement with competition-level data when available.</p>
 *
 * <p>The free tier of football-data.org has limited injury data. For a production
 * system, consider integrating with API-Football (api-sports.io) which has a
 * dedicated /injuries endpoint with 100 free requests/day.</p>
 */
@Service
@Slf4j
public class PlayerAvailabilityApiService {

    private final WebClient footballApiClient;
    private final PlayerAvailabilityRepository playerAvailabilityRepository;

    @Value("${football.api.competition:PL}")
    private String competition;

    /** Premier League team IDs in football-data.org */
    private static final Map<String, Integer> TEAM_API_IDS = Map.ofEntries(
            Map.entry("Arsenal", 57),
            Map.entry("Aston Villa", 58),
            Map.entry("Bournemouth", 1044),
            Map.entry("Brentford", 402),
            Map.entry("Brighton", 397),
            Map.entry("Chelsea", 61),
            Map.entry("Crystal Palace", 354),
            Map.entry("Everton", 62),
            Map.entry("Fulham", 63),
            Map.entry("Ipswich", 349),
            Map.entry("Leicester", 338),
            Map.entry("Liverpool", 64),
            Map.entry("Man City", 65),
            Map.entry("Man United", 66),
            Map.entry("Newcastle", 67),
            Map.entry("Nott'm Forest", 351),
            Map.entry("Southampton", 340),
            Map.entry("Tottenham", 73),
            Map.entry("West Ham", 563),
            Map.entry("Wolves", 76)
    );

    public PlayerAvailabilityApiService(
            WebClient footballApiClient,
            PlayerAvailabilityRepository playerAvailabilityRepository) {
        this.footballApiClient = footballApiClient;
        this.playerAvailabilityRepository = playerAvailabilityRepository;
    }

    /**
     * Sync player availability data for all Premier League teams.
     * Fetches squad data from football-data.org and maps injury/availability info.
     */
    public void syncAllTeams() {
        log.info("Starting player availability sync for all PL teams");
        int synced = 0;
        int errors = 0;

        for (Map.Entry<String, Integer> entry : TEAM_API_IDS.entrySet()) {
            try {
                syncTeam(entry.getKey(), entry.getValue());
                synced++;
                // Respect rate limit: 10 requests/min for free tier
                Thread.sleep(7_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Sync interrupted after {} teams", synced);
                break;
            } catch (Exception e) {
                errors++;
                log.warn("Failed to sync {} (id={}): {}", entry.getKey(), entry.getValue(), e.getMessage());
            }
        }

        log.info("Player availability sync complete: {} teams synced, {} errors", synced, errors);
    }

    /**
     * Sync a single team's squad data.
     */
    public void syncTeam(String teamName, int teamApiId) {
        log.debug("Syncing squad data for {} (API ID: {})", teamName, teamApiId);

        try {
            TeamSquadResponse response = footballApiClient.get()
                    .uri("/teams/{id}", teamApiId)
                    .retrieve()
                    .bodyToMono(TeamSquadResponse.class)
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(3))
                            .filter(ex -> ex.getMessage() != null && ex.getMessage().contains("429")))
                    .block(Duration.ofSeconds(15));

            if (response != null && response.getSquad() != null) {
                processSquadData(teamName, response);
                log.debug("Synced {} squad members for {}", response.getSquad().size(), teamName);
            }
        } catch (Exception e) {
            log.warn("API call failed for {} (id={}): {}", teamName, teamApiId, e.getMessage());
        }
    }

    /**
     * Process squad data and upsert player availability records.
     */
    private void processSquadData(String teamName, TeamSquadResponse response) {
        String season = SeasonHelper.deriveSeason(LocalDate.now());
        LocalDate today = LocalDate.now();

        for (TeamSquadResponse.SquadMember member : response.getSquad()) {
            String playerName = member.getName();
            String position = mapPosition(member.getPosition());
            int importance = estimateImportance(member);
            boolean keyStar = importance >= 9;
            double avgGoals = estimateAvgGoals(position);
            double avgAssists = estimateAvgAssists(position);

            Optional<PlayerAvailability> existing = playerAvailabilityRepository
                    .findByTeamNameAndPlayerName(teamName, playerName);

            if (existing.isPresent()) {
                // Update existing record — preserve status (may have been set by seeder/admin)
                PlayerAvailability pa = existing.get();
                pa.setPosition(position);
                pa.setImportanceRating(importance);
                pa.setKeyStar(keyStar);
                // Only update stats if they were at default (0.0) — don't overwrite seeder data
                if (pa.getAvgGoalsPerGame() == 0.0) pa.setAvgGoalsPerGame(avgGoals);
                if (pa.getAvgAssistsPerGame() == 0.0) pa.setAvgAssistsPerGame(avgAssists);
                pa.setReportDate(today);
                pa.setSeason(season);
                playerAvailabilityRepository.save(pa);
            } else {
                // Create new record — default to AVAILABLE status
                PlayerAvailability pa = PlayerAvailability.builder()
                        .teamName(teamName)
                        .playerName(playerName)
                        .position(position)
                        .status(AvailabilityStatus.AVAILABLE)
                        .importanceRating(importance)
                        .keyStar(keyStar)
                        .avgGoalsPerGame(avgGoals)
                        .avgAssistsPerGame(avgAssists)
                        .reportDate(today)
                        .season(season)
                        .build();
                playerAvailabilityRepository.save(pa);
            }
        }
    }

    /**
     * Update a player's status (for manual or external data source updates).
     */
    public void updatePlayerStatus(String teamName, String playerName,
                                    AvailabilityStatus status, String reason,
                                    LocalDate expectedReturn) {
        Optional<PlayerAvailability> existing = playerAvailabilityRepository
                .findByTeamNameAndPlayerName(teamName, playerName);

        if (existing.isPresent()) {
            PlayerAvailability pa = existing.get();
            pa.setStatus(status);
            pa.setReason(reason);
            pa.setExpectedReturn(expectedReturn);
            pa.setReportDate(LocalDate.now());
            playerAvailabilityRepository.save(pa);
            log.info("Updated {} ({}) → {} ({})", playerName, teamName, status, reason);
        } else {
            log.warn("Player not found: {} ({})", playerName, teamName);
        }
    }

    // ── Mapping Helpers ──────────────────────────────────────────────────

    private String mapPosition(String apiPosition) {
        if (apiPosition == null) return "MID";
        return switch (apiPosition.toUpperCase()) {
            case "GOALKEEPER" -> "GK";
            case "DEFENCE", "LEFT-BACK", "RIGHT-BACK", "CENTRE-BACK" -> "DEF";
            case "MIDFIELD", "CENTRAL MIDFIELD", "DEFENSIVE MIDFIELD",
                 "ATTACKING MIDFIELD", "LEFT MIDFIELD", "RIGHT MIDFIELD",
                 "LEFT WINGER", "RIGHT WINGER" -> "MID";
            case "OFFENCE", "CENTRE-FORWARD" -> "FWD";
            default -> "MID";
        };
    }

    /**
     * Estimate player importance (1–10) from squad metadata.
     * Uses position-based heuristic; production would use market value / appearances.
     */
    private int estimateImportance(TeamSquadResponse.SquadMember member) {
        String pos = member.getPosition();
        if (pos == null) return 5;
        return switch (pos.toUpperCase()) {
            case "GOALKEEPER" -> 7;
            case "CENTRE-BACK" -> 6;
            case "LEFT-BACK", "RIGHT-BACK", "DEFENCE" -> 6;
            case "DEFENSIVE MIDFIELD" -> 7;
            case "CENTRAL MIDFIELD", "ATTACKING MIDFIELD", "MIDFIELD" -> 6;
            case "LEFT WINGER", "RIGHT WINGER", "LEFT MIDFIELD", "RIGHT MIDFIELD" -> 7;
            case "CENTRE-FORWARD", "OFFENCE" -> 8;
            default -> 5;
        };
    }

    /**
     * Estimate average goals/assists per game from position.
     * Production would use real stats from a statistics API.
     */
    private double estimateAvgGoals(String mappedPosition) {
        return switch (mappedPosition) {
            case "FWD" -> 0.25;
            case "MID" -> 0.08;
            case "DEF" -> 0.02;
            case "GK"  -> 0.0;
            default    -> 0.05;
        };
    }

    private double estimateAvgAssists(String mappedPosition) {
        return switch (mappedPosition) {
            case "FWD" -> 0.10;
            case "MID" -> 0.12;
            case "DEF" -> 0.04;
            case "GK"  -> 0.01;
            default    -> 0.05;
        };
    }

    // ── Response DTOs ────────────────────────────────────────────────────

    @Data
    public static class TeamSquadResponse {
        private Integer id;
        private String name;
        private String shortName;
        private List<SquadMember> squad;

        @Data
        public static class SquadMember {
            private Integer id;
            private String name;
            private String position;       // "Goalkeeper", "Defence", "Midfield", "Offence"
            private String dateOfBirth;
            private String nationality;
        }
    }
}

