package com.app.footballprediction.polling.sse;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Event sent to clients when all matches for the day are completed.
 */
@Data
@Builder
public class MatchCompletionEvent {

    /**
     * Event type identifier.
     */
    private String type;

    /**
     * Date of completion (YYYY-MM-DD).
     */
    private String date;

    /**
     * Total matches completed.
     */
    private int matchesCompleted;

    /**
     * Timestamp of completion.
     */
    private LocalDateTime timestamp;

    /**
     * Human-readable message.
     */
    private String message;

    /**
     * Create an ALL_MATCHES_COMPLETED event.
     */
    public static MatchCompletionEvent allCompleted(String date, int matchCount) {
        return MatchCompletionEvent.builder()
            .type("ALL_MATCHES_COMPLETED")
            .date(date)
            .matchesCompleted(matchCount)
            .timestamp(LocalDateTime.now())
            .message("All " + matchCount + " matches completed for " + date)
            .build();
    }

    /**
     * Create a MATCH_RESULT_UPDATED event (for corrections).
     */
    public static MatchCompletionEvent resultUpdated(String date, int matchCount) {
        return MatchCompletionEvent.builder()
            .type("MATCH_RESULT_UPDATED")
            .date(date)
            .matchesCompleted(matchCount)
            .timestamp(LocalDateTime.now())
            .message("Match result updated for " + date)
            .build();
    }
}

