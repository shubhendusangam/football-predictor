package com.app.footballprediction.polling.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for match day status - used for smart dashboard refresh.
 * Frontend uses this to determine if/when to refresh.
 */
@Data
@Builder
public class MatchDayStatus {

    /**
     * Whether today is a match day (any matches scheduled).
     */
    private boolean matchDay;

    /**
     * Total number of matches scheduled for today.
     */
    private int totalMatchesToday;

    /**
     * Number of completed matches today.
     */
    private int completedMatchesToday;

    /**
     * Whether all matches for today are completed.
     * True when totalMatchesToday > 0 AND completedMatchesToday == totalMatchesToday
     */
    private boolean allMatchesCompleted;

    /**
     * Timestamp of the last match completion.
     */
    private LocalDateTime lastMatchCompletionTimestamp;

    /**
     * Today's date.
     */
    private String date;

    /**
     * Message for UI display.
     */
    private String statusMessage;
}

