package com.app.common.repository;

import com.app.common.model.ModelAccuracy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ModelAccuracy entity operations.
 * Provides queries for accuracy tracking across scopes (global, league, team).
 */
@Repository
public interface ModelAccuracyRepository extends JpaRepository<ModelAccuracy, Long> {

    /**
     * Find the latest accuracy entry for a given scope and key.
     */
    Optional<ModelAccuracy> findTopByScopeAndScopeKeyOrderByCalculatedAtDesc(String scope, String scopeKey);

    /**
     * Find the latest global accuracy.
     */
    @Query("SELECT a FROM ModelAccuracy a WHERE a.scope = 'GLOBAL' ORDER BY a.calculatedAt DESC")
    List<ModelAccuracy> findLatestGlobalAccuracy();

    /**
     * Find latest accuracy for all teams.
     */
    @Query("SELECT a FROM ModelAccuracy a WHERE a.scope = 'TEAM' AND a.calculatedAt = " +
           "(SELECT MAX(a2.calculatedAt) FROM ModelAccuracy a2 WHERE a2.scope = 'TEAM' AND a2.scopeKey = a.scopeKey)")
    List<ModelAccuracy> findLatestTeamAccuracies();

    /**
     * Find latest accuracy for all leagues.
     */
    @Query("SELECT a FROM ModelAccuracy a WHERE a.scope = 'LEAGUE' AND a.calculatedAt = " +
           "(SELECT MAX(a2.calculatedAt) FROM ModelAccuracy a2 WHERE a2.scope = 'LEAGUE' AND a2.scopeKey = a.scopeKey)")
    List<ModelAccuracy> findLatestLeagueAccuracies();

    /**
     * Find accuracy history for a scope and key.
     */
    List<ModelAccuracy> findByScopeAndScopeKeyOrderByCalculatedAtDesc(String scope, String scopeKey);

    /**
     * Find accuracy entries by season.
     */
    List<ModelAccuracy> findBySeasonOrderByCalculatedAtDesc(String season);

    /**
     * Delete old accuracy records, keeping only the most recent N per scope/key.
     */
    @Query("SELECT a FROM ModelAccuracy a WHERE a.scope = :scope AND a.scopeKey = :scopeKey ORDER BY a.calculatedAt DESC")
    List<ModelAccuracy> findByScopeAndKey(@Param("scope") String scope, @Param("scopeKey") String scopeKey);
}

