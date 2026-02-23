package com.app.common.repository;

import com.app.common.model.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for SystemSettings entity.
 * Only one row should exist in this table.
 */
@Repository
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {

    /**
     * Get the single settings row.
     * Returns the first (and should be only) settings entry.
     */
    default Optional<SystemSettings> getSettings() {
        return findAll().stream().findFirst();
    }
}

