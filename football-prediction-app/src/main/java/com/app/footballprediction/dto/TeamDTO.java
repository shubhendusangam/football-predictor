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

