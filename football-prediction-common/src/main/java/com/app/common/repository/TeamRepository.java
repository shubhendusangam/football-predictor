package com.app.common.repository;

import com.app.common.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * Repository for Team entity operations.
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    /**
     * Find a team by its exact name.
     */
    Optional<Team> findByName(String name);

    /**
     * Find a team by its name, case-insensitive.
     */
    Optional<Team> findByNameIgnoreCase(String name);

    /**
     * Find teams by partial name match (case-insensitive).
     */
    List<Team> findByNameContainingIgnoreCase(String namePart);

    /**
     * Check if a team exists by name.
     */
    boolean existsByName(String name);

    /**
     * Find all teams ordered by name.
     */
    List<Team> findAllByOrderByNameAsc();
}

