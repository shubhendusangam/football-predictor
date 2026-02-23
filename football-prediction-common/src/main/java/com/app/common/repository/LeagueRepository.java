package com.app.common.repository;

import com.app.common.model.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for League entity.
 */
@Repository
public interface LeagueRepository extends JpaRepository<League, Long> {

    /**
     * Find league by code.
     */
    Optional<League> findByCode(String code);

    /**
     * Find all enabled leagues ordered by display order.
     */
    List<League> findByEnabledTrueOrderByDisplayOrderAsc();

    /**
     * Find all leagues ordered by display order.
     */
    List<League> findAllByOrderByDisplayOrderAsc();

    /**
     * Check if a league exists by code.
     */
    boolean existsByCode(String code);
}

