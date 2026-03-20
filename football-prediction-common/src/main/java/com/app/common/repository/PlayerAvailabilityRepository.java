package com.app.common.repository;

import com.app.common.model.PlayerAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for player availability / injury tracking.
 */
@Repository
public interface PlayerAvailabilityRepository extends JpaRepository<PlayerAvailability, Long> {

    /**
     * Find all currently unavailable players for a team.
     * A player is "active absent" if their status is INJURED/SUSPENDED/DOUBTFUL
     * AND (expectedReturn is null OR expectedReturn > the given date).
     */
    @Query("SELECT p FROM PlayerAvailability p WHERE p.teamName = :team " +
            "AND p.status IN ('INJURED', 'SUSPENDED', 'DOUBTFUL') " +
            "AND (p.expectedReturn IS NULL OR p.expectedReturn > :date) " +
            "ORDER BY p.importanceRating DESC")
    List<PlayerAvailability> findActiveAbsences(
            @Param("team") String teamName,
            @Param("date") LocalDate date);

    /**
     * Find all currently unavailable players for a team within a season.
     */
    @Query("SELECT p FROM PlayerAvailability p WHERE p.teamName = :team " +
            "AND p.season = :season " +
            "AND p.status IN ('INJURED', 'SUSPENDED', 'DOUBTFUL') " +
            "AND (p.expectedReturn IS NULL OR p.expectedReturn > :date) " +
            "ORDER BY p.importanceRating DESC")
    List<PlayerAvailability> findActiveAbsencesBySeason(
            @Param("team") String teamName,
            @Param("season") String season,
            @Param("date") LocalDate date);

    /** Find all records for a team (any status). */
    List<PlayerAvailability> findByTeamNameOrderByImportanceRatingDesc(String teamName);

    /** Find by team + player for upsert. */
    Optional<PlayerAvailability> findByTeamNameAndPlayerName(String teamName, String playerName);

    /** Find all key stars for a team. */
    List<PlayerAvailability> findByTeamNameAndKeyStarTrue(String teamName);

    /** Delete old records before a given date (cleanup). */
    @Modifying
    @Transactional
    @Query("DELETE FROM PlayerAvailability p WHERE p.reportDate < :before")
    void deleteOlderThan(@Param("before") LocalDate before);

    /** Count unavailable players for a team. */
    @Query("SELECT COUNT(p) FROM PlayerAvailability p WHERE p.teamName = :team " +
            "AND p.status IN ('INJURED', 'SUSPENDED', 'DOUBTFUL') " +
            "AND (p.expectedReturn IS NULL OR p.expectedReturn > :date)")
    long countActiveAbsences(@Param("team") String teamName, @Param("date") LocalDate date);

    /** Get all distinct team names that have availability data. */
    @Query("SELECT DISTINCT p.teamName FROM PlayerAvailability p ORDER BY p.teamName")
    List<String> findAllTeamNames();
}
