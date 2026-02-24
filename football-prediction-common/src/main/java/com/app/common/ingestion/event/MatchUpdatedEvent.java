package com.app.common.ingestion.event;

import com.app.common.model.Match;
import org.springframework.context.ApplicationEvent;

/**
 * Domain event published when a match is updated or inserted.
 *
 * <p>Listeners can react to this event to:
 * <ul>
 *   <li>Update season team statistics</li>
 *   <li>Refresh insight calculations</li>
 *   <li>Invalidate related caches</li>
 *   <li>Trigger model retraining (if significant changes)</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> Listeners should use {@code @TransactionalEventListener}
 * with {@code phase = AFTER_COMMIT} to ensure the match is persisted before
 * processing. This prevents inconsistent state if the transaction rolls back.
 */
public class MatchUpdatedEvent extends ApplicationEvent {

    private final Match match;
    private final UpdateType updateType;

    /**
     * Create a match updated event.
     *
     * @param source Event source
     * @param match The match that was updated
     */
    public MatchUpdatedEvent(Object source, Match match) {
        this(source, match, UpdateType.UNKNOWN);
    }

    /**
     * Create a match updated event with specific update type.
     *
     * @param source Event source
     * @param match The match that was updated
     * @param updateType Type of update performed
     */
    public MatchUpdatedEvent(Object source, Match match, UpdateType updateType) {
        super(source);
        this.match = match;
        this.updateType = updateType;
    }

    /**
     * Get the updated match.
     */
    public Match getMatch() {
        return match;
    }

    /**
     * Get the type of update.
     */
    public UpdateType getUpdateType() {
        return updateType;
    }

    /**
     * Check if this was a new match insertion.
     */
    public boolean isNewMatch() {
        return updateType == UpdateType.INSERTED;
    }

    /**
     * Check if match result was updated (e.g., score correction).
     */
    public boolean isResultUpdate() {
        return updateType == UpdateType.RESULT_UPDATED;
    }

    /**
     * Types of match updates.
     */
    public enum UpdateType {
        /** New match inserted */
        INSERTED,

        /** Match result was set (match completed) */
        RESULT_UPDATED,

        /** Match statistics updated (shots, corners, etc.) */
        STATS_UPDATED,

        /** Match was rescheduled */
        RESCHEDULED,

        /** Unknown update type */
        UNKNOWN
    }
}

