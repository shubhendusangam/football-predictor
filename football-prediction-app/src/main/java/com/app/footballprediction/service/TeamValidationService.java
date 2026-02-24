package com.app.footballprediction.service;

import com.app.common.repository.MatchRepository;
import com.app.common.util.TeamNameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for validating team names against known teams in the database.
 * Provides team name normalization and fuzzy matching capabilities.
 *
 * <p>This service maintains a cache of valid teams for the current season
 * and handles common aliases (e.g., Tottenham = Spurs).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamValidationService {

    private final MatchRepository matchRepository;

    /**
     * Common team name aliases that map to canonical database names.
     * Key: alias (lowercase), Value: canonical name
     */
    private static final Map<String, String> TEAM_ALIASES = Map.ofEntries(
            // Tottenham aliases
            Map.entry("spurs", "Tottenham"),
            Map.entry("tottenham hotspur", "Tottenham"),
            Map.entry("tottenham hotspur fc", "Tottenham"),
            // Manchester United aliases
            Map.entry("man utd", "Man United"),
            Map.entry("manchester united", "Man United"),
            Map.entry("manchester united fc", "Man United"),
            Map.entry("mufc", "Man United"),
            // Manchester City aliases
            Map.entry("man city", "Man City"),
            Map.entry("manchester city", "Man City"),
            Map.entry("manchester city fc", "Man City"),
            Map.entry("mcfc", "Man City"),
            // West Ham aliases
            Map.entry("west ham utd", "West Ham"),
            Map.entry("west ham united", "West Ham"),
            Map.entry("west ham united fc", "West Ham"),
            // Newcastle aliases
            Map.entry("newcastle utd", "Newcastle"),
            Map.entry("newcastle united", "Newcastle"),
            Map.entry("newcastle united fc", "Newcastle"),
            // Wolves aliases
            Map.entry("wolverhampton", "Wolves"),
            Map.entry("wolverhampton wanderers", "Wolves"),
            Map.entry("wolverhampton wanderers fc", "Wolves"),
            // Brighton aliases
            Map.entry("brighton and hove albion", "Brighton"),
            Map.entry("brighton & hove albion", "Brighton"),
            Map.entry("brighton & hove albion fc", "Brighton"),
            // Nottingham Forest aliases
            Map.entry("nottingham forest", "Nott'm Forest"),
            Map.entry("notts forest", "Nott'm Forest"),
            Map.entry("nottingham forest fc", "Nott'm Forest"),
            // Leicester aliases
            Map.entry("leicester city", "Leicester"),
            Map.entry("leicester city fc", "Leicester"),
            // Sheffield United aliases
            Map.entry("sheffield utd", "Sheffield United"),
            Map.entry("sheffield united fc", "Sheffield United"),
            // Other common aliases
            Map.entry("arsenal fc", "Arsenal"),
            Map.entry("chelsea fc", "Chelsea"),
            Map.entry("liverpool fc", "Liverpool"),
            Map.entry("everton fc", "Everton"),
            Map.entry("afc bournemouth", "Bournemouth"),
            Map.entry("brentford fc", "Brentford"),
            Map.entry("fulham fc", "Fulham"),
            Map.entry("crystal palace fc", "Crystal Palace"),
            Map.entry("aston villa fc", "Aston Villa"),
            Map.entry("ipswich town", "Ipswich"),
            Map.entry("ipswich town fc", "Ipswich"),
            Map.entry("southampton fc", "Southampton")
    );

    /**
     * Validate and normalize a team name.
     *
     * @param teamName The team name to validate
     * @return ValidationResult containing normalized name and validation status
     */
    public ValidationResult validateTeam(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return ValidationResult.invalid("Team name cannot be empty", null);
        }

        String trimmed = teamName.trim();

        // Step 1: Check alias mapping
        String aliasMatch = TEAM_ALIASES.get(trimmed.toLowerCase());
        if (aliasMatch != null) {
            return ValidationResult.valid(aliasMatch);
        }

        // Step 2: Use TeamNameNormalizer
        String normalized = TeamNameNormalizer.normalize(trimmed);

        // Step 3: Check if normalized name exists in database
        Set<String> validTeams = getValidTeams();
        if (validTeams.contains(normalized)) {
            return ValidationResult.valid(normalized);
        }

        // Step 4: Try case-insensitive match against valid teams
        for (String validTeam : validTeams) {
            if (validTeam.equalsIgnoreCase(normalized) || validTeam.equalsIgnoreCase(trimmed)) {
                return ValidationResult.valid(validTeam);
            }
        }

        // Step 5: Try fuzzy matching
        List<String> suggestions = findSimilarTeams(trimmed);
        if (!suggestions.isEmpty()) {
            return ValidationResult.invalid(
                    "Unknown team: '" + teamName + "'",
                    suggestions
            );
        }

        return ValidationResult.invalid(
                "Unknown team: '" + teamName + "'. Use GET /api/teams to see valid team names.",
                Collections.emptyList()
        );
    }

    /**
     * Get all valid team names from the current season.
     * Results are cached for performance.
     */
    @Cacheable(value = "validTeams", unless = "#result.isEmpty()")
    public Set<String> getValidTeams() {
        List<String> teams = matchRepository.findAllDistinctTeamNames();
        return new TreeSet<>(teams);
    }

    /**
     * Get valid teams for a specific season.
     */
    @Cacheable(value = "validTeamsBySeason", key = "#season")
    public Set<String> getValidTeamsForSeason(String season) {
        List<String> teams = matchRepository.findAllDistinctTeamNamesBySeason(season);
        return new TreeSet<>(teams);
    }

    /**
     * Check if a team name is valid (exists in database).
     */
    public boolean isValidTeam(String teamName) {
        return validateTeam(teamName).isValid();
    }

    /**
     * Find teams with similar names for suggestions.
     */
    private List<String> findSimilarTeams(String teamName) {
        String searchTerm = teamName.toLowerCase();
        Set<String> validTeams = getValidTeams();
        List<String> suggestions = new ArrayList<>();

        for (String team : validTeams) {
            String teamLower = team.toLowerCase();
            // Check if any word matches
            if (teamLower.contains(searchTerm) || searchTerm.contains(teamLower)) {
                suggestions.add(team);
            }
            // Check Levenshtein-like similarity (simple version)
            else if (calculateSimilarity(teamLower, searchTerm) > 0.6) {
                suggestions.add(team);
            }
        }

        // Limit suggestions
        return suggestions.subList(0, Math.min(5, suggestions.size()));
    }

    /**
     * Simple similarity calculation (normalized common substring ratio).
     */
    private double calculateSimilarity(String s1, String s2) {
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;

        int matches = 0;
        int minLen = Math.min(s1.length(), s2.length());
        for (int i = 0; i < minLen; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                matches++;
            }
        }
        return (double) matches / maxLen;
    }

    /**
     * Result of team validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String normalizedName;
        private final String errorMessage;
        private final List<String> suggestions;

        private ValidationResult(boolean valid, String normalizedName, String errorMessage, List<String> suggestions) {
            this.valid = valid;
            this.normalizedName = normalizedName;
            this.errorMessage = errorMessage;
            this.suggestions = suggestions != null ? suggestions : Collections.emptyList();
        }

        public static ValidationResult valid(String normalizedName) {
            return new ValidationResult(true, normalizedName, null, null);
        }

        public static ValidationResult invalid(String errorMessage, List<String> suggestions) {
            return new ValidationResult(false, null, errorMessage, suggestions);
        }

        public boolean isValid() {
            return valid;
        }

        public String getNormalizedName() {
            return normalizedName;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public List<String> getSuggestions() {
            return suggestions;
        }

        public Map<String, Object> toErrorResponse() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("error", errorMessage);
            if (!suggestions.isEmpty()) {
                response.put("suggestions", suggestions);
            }
            response.put("hint", "Use GET /api/teams to see all valid team names");
            return response;
        }
    }
}

