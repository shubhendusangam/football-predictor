package com.app.footballprediction.ingestion.provider.legacy;

import com.app.common.ingestion.dto.InternalMatchDto;
import com.app.common.ingestion.mapper.CanonicalMapper;
import com.app.common.ingestion.provider.MatchDataProvider;
import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Legacy adapter wrapping existing Match repository data.
 * Implements MatchDataProvider to participate in new architecture
 * while preserving all existing behavior.
 *
 * <p>This adapter:
 * <ul>
 *   <li>Reads from existing database via MatchRepository</li>
 *   <li>Maps Match entities to canonical InternalMatchDto</li>
 *   <li>Does NOT modify any existing data or behavior</li>
 *   <li>Provides fallback when live APIs are unavailable</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> This is a READ-ONLY adapter. It does not
 * perform any data ingestion or modification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyCsvProvider implements MatchDataProvider {

    private static final String PROVIDER_NAME = "LEGACY_CSV";

    private final MatchRepository matchRepository;
    private final CanonicalMapper mapper;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public List<InternalMatchDto> getMatchesBySeason(String season) {
        log.debug("Fetching matches for season {} from legacy database", season);

        try {
            List<Match> matches = matchRepository.findBySeason(season);

            if (matches.isEmpty()) {
                log.debug("No matches found for season {} in legacy database", season);
                return Collections.emptyList();
            }

            List<InternalMatchDto> dtos = matches.stream()
                .map(m -> mapper.toDto(m, PROVIDER_NAME))
                .collect(Collectors.toList());

            log.debug("Retrieved {} matches for season {} from legacy database", dtos.size(), season);
            return dtos;

        } catch (Exception e) {
            log.error("Failed to fetch matches for season {} from legacy database: {}",
                season, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<InternalMatchDto> getRecentCompletedMatches(String competition, int limit) {
        log.debug("Fetching {} recent completed matches from legacy database", limit);

        try {
            List<Match> matches = matchRepository.findAllByOrderByMatchDateDesc()
                .stream()
                .filter(m -> m.getFullTimeResult() != null)
                .limit(limit)
                .collect(Collectors.toList());

            return matches.stream()
                .map(m -> mapper.toDto(m, PROVIDER_NAME))
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to fetch recent matches from legacy database: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<InternalMatchDto> getScheduledMatches(String competition) {
        // CSV/database doesn't have scheduled matches - return empty
        // Scheduled matches come from live APIs only
        log.debug("Legacy CSV provider does not support scheduled matches");
        return Collections.emptyList();
    }

    @Override
    public boolean isAvailable() {
        try {
            // Simple availability check - can we count matches?
            matchRepository.count();
            return true;
        } catch (Exception e) {
            log.warn("Legacy database not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public int getPriority() {
        // Lower priority than live APIs
        // Used as fallback when APIs are unavailable
        return 100;
    }

    @Override
    public List<String> getSupportedCompetitions() {
        // Our CSV data is Premier League only
        return List.of("PL");
    }

    /**
     * Get count of matches in database.
     * Useful for diagnostics.
     */
    public long getMatchCount() {
        return matchRepository.count();
    }

    /**
     * Get available seasons in database.
     */
    public List<String> getAvailableSeasons() {
        return matchRepository.findAllSeasons();
    }
}

