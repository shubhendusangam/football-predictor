package com.app.common.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a football team with logo support.
 */
@Entity
@Table(name = "teams")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    /**
     * URL to the team's logo image.
     * Can be a relative path (e.g., "/images/teams/arsenal.png")
     * or an absolute URL to an external source.
     */
    @Column(name = "logo_url")
    private String logoUrl;

    /**
     * Short name or abbreviation (e.g., "ARS" for Arsenal)
     */
    @Column(name = "short_name")
    private String shortName;

    /**
     * Team's primary color (hex code, e.g., "#EF0107")
     */
    @Column(name = "primary_color")
    private String primaryColor;
}

