package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Team data including logo URL.
 * Used for API responses that need team information with logos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDTO {

    /**
     * Team's full name (e.g., "Arsenal")
     */
    private String name;

    /**
     * URL to the team's logo image.
     * Relative path (e.g., "/images/teams/arsenal.png") or absolute URL.
     */
    private String logoUrl;

    /**
     * Short name or abbreviation (e.g., "ARS")
     */
    private String shortName;

    /**
     * Team's primary color (hex code)
     */
    private String primaryColor;

    /**
     * Current league position (1-20 for Premier League).
     * Only populated for current season.
     */
    private Integer position;

    /**
     * Team status based on league standing.
     * Values: "promoted", "relegation", "safe", null
     * - "promoted": Team was promoted from lower division this season (new to the league)
     * - "relegation": Team is in relegation zone (bottom 3)
     * - "safe": Team is safe from relegation
     * - null: Status unknown or historical season
     */
    private String status;

    /**
     * Zone classification for current season.
     * Values: "champions", "europa", "conference", "mid", "relegation"
     */
    private String zone;

    /**
     * Default fallback logo - online CDN football icon
     */
    public static final String DEFAULT_LOGO = "https://cdn-icons-png.flaticon.com/512/861/861512.png";

    /**
     * Get logo URL with fallback to default if null or empty.
     */
    public String getLogoUrlOrDefault() {
        return (logoUrl != null && !logoUrl.isEmpty()) ? logoUrl : DEFAULT_LOGO;
    }

    /**
     * Create a TeamDTO from just a team name (for backward compatibility).
     * Uses default logo.
     */
    public static TeamDTO fromName(String name) {
        return TeamDTO.builder()
                .name(name)
                .logoUrl(DEFAULT_LOGO)
                .build();
    }
}

