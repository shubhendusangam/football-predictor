package com.app.common.ingestion.event;

import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.Set;

/**
 * Domain event published when season statistics need to be refreshed.
 *
 * <p>This event is published after match updates to trigger
 * recalculation of SeasonTeamStats for affected seasons.
 *
 * <p>Listeners should call EXISTING stats calculation logic.
 * No new calculation algorithms should be introduced.
 */
public class StatsRefreshEvent extends ApplicationEvent {

    private final Set<String> affectedSeasons;
    private final Set<String> affectedTeams;
    private final RefreshScope scope;

    /**
     * Create a stats refresh event for specific seasons.
     *
     * @param source Event source
     * @param affectedSeasons Seasons that need refresh
     */
    public StatsRefreshEvent(Object source, Set<String> affectedSeasons) {
        this(source, affectedSeasons, Collections.emptySet(), RefreshScope.FULL);
    }

    /**
     * Create a stats refresh event with specific scope.
     *
     * @param source Event source
     * @param affectedSeasons Seasons that need refresh
     * @param affectedTeams Teams that need refresh (for partial refresh)
     * @param scope Refresh scope
     */
    public StatsRefreshEvent(Object source, Set<String> affectedSeasons,
                             Set<String> affectedTeams, RefreshScope scope) {
        super(source);
        this.affectedSeasons = affectedSeasons;
        this.affectedTeams = affectedTeams;
        this.scope = scope;
    }

    /**
     * Get seasons that need stats refresh.
     */
    public Set<String> getAffectedSeasons() {
        return affectedSeasons;
    }

    /**
     * Get teams that need stats refresh.
     * Only relevant for PARTIAL scope.
     */
    public Set<String> getAffectedTeams() {
        return affectedTeams;
    }

    /**
     * Get the refresh scope.
     */
    public RefreshScope getScope() {
        return scope;
    }

    /**
     * Scope of the stats refresh.
     */
    public enum RefreshScope {
        /** Refresh all teams in affected seasons */
        FULL,

        /** Refresh only specific teams */
        PARTIAL,

        /** Incremental update (add to existing) */
        INCREMENTAL
    }
}

