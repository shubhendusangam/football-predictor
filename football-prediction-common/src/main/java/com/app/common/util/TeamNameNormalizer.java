package com.app.common.util;

import java.util.Map;

/**
 * Utility class for normalizing team names between different formats.
 *
 * External APIs (like football-data.org) use full official names with "FC" suffix,
 * while our database uses shorter common names.
 *
 * Example:
 * - "Crystal Palace FC" -> "Crystal Palace"
 * - "Wolverhampton Wanderers FC" -> "Wolves"
 * - "Manchester United FC" -> "Man United"
 */
public final class TeamNameNormalizer {

    private TeamNameNormalizer() {
        // Utility class - prevent instantiation
    }

    /**
     * Team name mappings from external API names to database names.
     * Maps full official names to shorter common names used in our database.
     */
    private static final Map<String, String> TEAM_NAME_MAPPINGS = Map.ofEntries(
            // Premier League
            Map.entry("Manchester United FC", "Man United"),
            Map.entry("Manchester City FC", "Man City"),
            Map.entry("Tottenham Hotspur FC", "Tottenham"),
            Map.entry("Newcastle United FC", "Newcastle"),
            Map.entry("West Ham United FC", "West Ham"),
            Map.entry("Wolverhampton Wanderers FC", "Wolves"),
            Map.entry("Leicester City FC", "Leicester"),
            Map.entry("Brighton & Hove Albion FC", "Brighton"),
            Map.entry("Nottingham Forest FC", "Nott'm Forest"),
            Map.entry("AFC Bournemouth", "Bournemouth"),
            Map.entry("Ipswich Town FC", "Ipswich"),
            Map.entry("Southampton FC", "Southampton"),
            Map.entry("Everton FC", "Everton"),
            Map.entry("Fulham FC", "Fulham"),
            Map.entry("Crystal Palace FC", "Crystal Palace"),
            Map.entry("Brentford FC", "Brentford"),
            Map.entry("Aston Villa FC", "Aston Villa"),
            Map.entry("Chelsea FC", "Chelsea"),
            Map.entry("Arsenal FC", "Arsenal"),
            Map.entry("Liverpool FC", "Liverpool"),

            // Championship and other common teams
            Map.entry("Leeds United FC", "Leeds"),
            Map.entry("Sheffield United FC", "Sheffield United"),
            Map.entry("Sheffield Wednesday FC", "Sheffield Weds"),
            Map.entry("West Bromwich Albion FC", "West Brom"),
            Map.entry("Queens Park Rangers FC", "QPR"),
            Map.entry("Blackburn Rovers FC", "Blackburn"),
            Map.entry("Bolton Wanderers FC", "Bolton"),
            Map.entry("Stoke City FC", "Stoke"),
            Map.entry("Sunderland AFC", "Sunderland"),
            Map.entry("Watford FC", "Watford"),
            Map.entry("Norwich City FC", "Norwich"),
            Map.entry("Burnley FC", "Burnley"),
            Map.entry("Middlesbrough FC", "Middlesbrough"),
            Map.entry("Swansea City AFC", "Swansea"),
            Map.entry("Cardiff City FC", "Cardiff"),
            Map.entry("Huddersfield Town AFC", "Huddersfield"),

            // Alternative spellings
            Map.entry("Spurs", "Tottenham"),
            Map.entry("Man Utd", "Man United"),
            Map.entry("Man City", "Man City"),
            Map.entry("West Ham Utd", "West Ham"),
            Map.entry("Newcastle Utd", "Newcastle"),
            Map.entry("Sheffield Utd", "Sheffield United")
    );

    /**
     * Normalize team name from API format to database format.
     * Uses only explicit mappings for name resolution.
     *
     * @param teamName The team name to normalize
     * @return The normalized team name matching database format, or original name if no mapping exists
     */
    public static String normalize(String teamName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            return teamName;
        }

        String trimmed = teamName.trim();

        // Use only explicit mappings for name resolution
        String mapped = TEAM_NAME_MAPPINGS.get(trimmed);
        return mapped != null ? mapped : trimmed;
    }

    /**
     * Check if a team name is in API format (with FC suffix).
     *
     * @param teamName The team name to check
     * @return true if the name appears to be in API format
     */
    public static boolean isApiFormat(String teamName) {
        if (teamName == null) return false;
        return teamName.endsWith(" FC") ||
               teamName.endsWith(" AFC") ||
               TEAM_NAME_MAPPINGS.containsKey(teamName);
    }

    /**
     * Get the API format name for a database team name.
     * Returns the original name if no mapping exists.
     *
     * @param dbTeamName The database team name
     * @return The API format team name
     */
    public static String toApiFormat(String dbTeamName) {
        if (dbTeamName == null) return null;

        // Reverse lookup in mappings
        for (Map.Entry<String, String> entry : TEAM_NAME_MAPPINGS.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(dbTeamName)) {
                return entry.getKey();
            }
        }

        // Default: append FC if not already present
        if (!dbTeamName.endsWith(" FC") && !dbTeamName.endsWith(" AFC")) {
            return dbTeamName + " FC";
        }

        return dbTeamName;
    }

    /**
     * Check if two team names refer to the same team.
     * Compares normalized versions of both names.
     *
     * @param name1 First team name
     * @param name2 Second team name
     * @return true if both names refer to the same team
     */
    public static boolean isSameTeam(String name1, String name2) {
        if (name1 == null || name2 == null) return false;

        String normalized1 = normalize(name1);
        String normalized2 = normalize(name2);

        return normalized1.equalsIgnoreCase(normalized2);
    }
}

