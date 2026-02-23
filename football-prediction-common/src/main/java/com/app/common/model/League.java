package com.app.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a football league/competition.
 */
@Entity
@Table(name = "leagues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * League code (e.g., "PL", "PD", "BL1", "SA", "FL1").
     */
    @Column(name = "code", unique = true, nullable = false, length = 10)
    private String code;

    /**
     * Full league name (e.g., "Premier League", "La Liga").
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Country code (e.g., "ENG", "ESP", "GER").
     */
    @Column(name = "country_code", length = 10)
    private String countryCode;

    /**
     * Country name (e.g., "England", "Spain").
     */
    @Column(name = "country_name")
    private String countryName;

    /**
     * URL to the league logo.
     */
    @Column(name = "logo_url")
    private String logoUrl;

    /**
     * Whether this league is enabled for predictions.
     */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Priority order for display (lower = higher priority).
     */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 100;

    /**
     * Current season (e.g., "2025-26").
     */
    @Column(name = "current_season")
    private String currentSeason;
}

