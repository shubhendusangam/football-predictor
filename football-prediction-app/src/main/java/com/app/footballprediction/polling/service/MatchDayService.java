package com.app.footballprediction.polling.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.polling.dto.MatchDayStatus;
import com.app.footballprediction.polling.sse.MatchCompletionEvent;
import com.app.footballprediction.polling.sse.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for tracking match day status.
 * Determines if it's a match day, how many matches are completed,
 * and triggers SSE events when all matches are done.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchDayService {

    private final MatchRepository matchRepository;
    private final SseEmitterService sseEmitterService;

    /**
     * Cache for today's match count to avoid frequent DB queries.
     * Reset daily.
     */
    private final AtomicReference<String> cachedDate = new AtomicReference<>("");
    private final AtomicReference<Integer> cachedTotalMatches = new AtomicReference<>(null);

    /**
     * Get current match day status.
     * Used by frontend to determine refresh behavior.
     */
    public MatchDayStatus getMatchDayStatus() {
        LocalDate today = LocalDate.now();
        String todayStr = today.toString();

        // Get matches for today
        List<Match> todayMatches = getTodayMatches(today);

        int totalMatches = todayMatches.size();
        int completedMatches = (int) todayMatches.stream()
            .filter(m -> m.getFullTimeResult() != null)
            .count();

        boolean isMatchDay = totalMatches > 0;
        boolean allCompleted = isMatchDay && completedMatches == totalMatches;

        // Find last completion timestamp
        LocalDateTime lastCompletion = todayMatches.stream()
            .filter(m -> m.getFullTimeResult() != null)
            .map(m -> m.getMatchDate().atStartOfDay()) // Use match date as proxy
            .max(LocalDateTime::compareTo)
            .orElse(null);

        // Build status message
        String message;
        if (!isMatchDay) {
            message = "No matches scheduled today";
        } else if (allCompleted) {
            message = "All " + totalMatches + " matches completed";
        } else {
            message = completedMatches + " of " + totalMatches + " matches completed";
        }

        return MatchDayStatus.builder()
            .matchDay(isMatchDay)
            .totalMatchesToday(totalMatches)
            .completedMatchesToday(completedMatches)
            .allMatchesCompleted(allCompleted)
            .lastMatchCompletionTimestamp(lastCompletion)
            .date(todayStr)
            .statusMessage(message)
            .build();
    }

    /**
     * Get matches for a specific date.
     */
    private List<Match> getTodayMatches(LocalDate date) {
        return matchRepository.findAllByOrderByMatchDateDesc().stream()
            .filter(m -> m.getMatchDate() != null && m.getMatchDate().equals(date))
            .toList();
    }

    /**
     * Check if all matches are completed and trigger SSE event if so.
     * Called after match upsert operations.
     */
    public void checkAndNotifyCompletion() {
        MatchDayStatus status = getMatchDayStatus();

        if (status.isAllMatchesCompleted() && !sseEmitterService.isCompletionSent(status.getDate())) {
            log.info("🎉 All {} matches completed for {}, broadcasting SSE event",
                status.getTotalMatchesToday(), status.getDate());

            MatchCompletionEvent event = MatchCompletionEvent.allCompleted(
                status.getDate(),
                status.getTotalMatchesToday()
            );

            sseEmitterService.broadcastCompletion(event);
        }
    }

    /**
     * Notify that a match result was updated/corrected.
     * Re-triggers refresh for clients.
     */
    public void notifyResultUpdate() {
        String today = LocalDate.now().toString();
        MatchDayStatus status = getMatchDayStatus();

        log.info("📝 Match result updated for {}, re-broadcasting SSE event", today);

        // Reset completion status to allow re-triggering
        sseEmitterService.resetCompletion(today);

        if (status.isAllMatchesCompleted()) {
            MatchCompletionEvent event = MatchCompletionEvent.resultUpdated(
                today,
                status.getCompletedMatchesToday()
            );
            sseEmitterService.broadcastCompletion(event);
        }
    }

    /**
     * Check if it's currently a match day.
     */
    public boolean isMatchDay() {
        return getMatchDayStatus().isMatchDay();
    }

    /**
     * Get total matches for today (cached for performance).
     */
    public int getTotalMatchesToday() {
        LocalDate today = LocalDate.now();
        String todayStr = today.toString();

        // Check cache
        if (todayStr.equals(cachedDate.get()) && cachedTotalMatches.get() != null) {
            return cachedTotalMatches.get();
        }

        // Query and cache
        int count = getTodayMatches(today).size();
        cachedDate.set(todayStr);
        cachedTotalMatches.set(count);

        return count;
    }

    /**
     * Invalidate cache (called after data changes).
     */
    public void invalidateCache() {
        cachedTotalMatches.set(null);
    }
}

