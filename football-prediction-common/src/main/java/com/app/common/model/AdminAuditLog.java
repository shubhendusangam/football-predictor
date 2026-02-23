package com.app.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for auditing admin actions.
 * Records all administrative operations for security and debugging purposes.
 */
@Entity
@Table(name = "admin_audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Username of the admin who performed the action.
     */
    @Column(name = "username", nullable = false)
    private String username;

    /**
     * Type of action performed.
     */
    @Column(name = "action_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    /**
     * Description of the action.
     */
    @Column(name = "action_description", length = 1000)
    private String actionDescription;

    /**
     * Target entity/resource of the action (e.g., "Match", "Team", "Settings").
     */
    @Column(name = "target_entity")
    private String targetEntity;

    /**
     * ID of the target entity if applicable.
     */
    @Column(name = "target_id")
    private String targetId;

    /**
     * Previous value (for update operations).
     */
    @Column(name = "previous_value", length = 2000)
    private String previousValue;

    /**
     * New value (for update operations).
     */
    @Column(name = "new_value", length = 2000)
    private String newValue;

    /**
     * IP address of the request.
     */
    @Column(name = "ip_address")
    private String ipAddress;

    /**
     * User agent of the request.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Whether the action was successful.
     */
    @Column(name = "success")
    @Builder.Default
    private Boolean success = true;

    /**
     * Error message if the action failed.
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * Timestamp when the action was performed.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void setCreatedAt() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Enum representing types of admin actions.
     */
    public enum ActionType {
        LOGIN,
        LOGOUT,
        TOGGLE_ENGINE,
        RETRAIN_MODEL,
        UPDATE_SETTINGS,
        MATCH_OVERRIDE,
        CLEAR_CACHE,
        FETCH_DATA,
        LEAGUE_UPDATE,
        TEAM_UPDATE,
        LOGO_UPDATE,
        SYSTEM_RESTART,
        BACKUP_CREATE,
        BACKUP_RESTORE,
        USER_MANAGEMENT
    }
}

