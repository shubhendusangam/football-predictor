package com.app.common.repository;

import com.app.common.model.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for AdminAuditLog entity.
 */
@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /**
     * Find audit logs by username.
     */
    List<AdminAuditLog> findByUsernameOrderByCreatedAtDesc(String username);

    /**
     * Find audit logs by action type.
     */
    List<AdminAuditLog> findByActionTypeOrderByCreatedAtDesc(AdminAuditLog.ActionType actionType);

    /**
     * Find recent audit logs with pagination.
     */
    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find audit logs within a date range.
     */
    @Query("SELECT a FROM AdminAuditLog a WHERE a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AdminAuditLog> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Find recent audit logs for a specific target entity.
     */
    List<AdminAuditLog> findByTargetEntityOrderByCreatedAtDesc(String targetEntity);

    /**
     * Count actions by type.
     */
    long countByActionType(AdminAuditLog.ActionType actionType);

    /**
     * Find failed actions.
     */
    List<AdminAuditLog> findBySuccessFalseOrderByCreatedAtDesc();
}

