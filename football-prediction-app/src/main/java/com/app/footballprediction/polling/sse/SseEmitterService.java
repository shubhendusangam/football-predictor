package com.app.footballprediction.polling.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing Server-Sent Events (SSE) connections.
 * Allows backend to push match completion events to frontend.
 *
 * <p>Clients connect via SSE and receive ONE event when all matches complete,
 * then the connection is closed. No polling required.
 */
@Service
@Slf4j
public class SseEmitterService {

    /**
     * Active SSE emitters keyed by date.
     * When all matches for a date complete, all emitters for that date receive the event.
     */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Track which dates have already sent completion events.
     * Prevents duplicate events on the same day.
     */
    private final Map<String, Boolean> completionSent = new ConcurrentHashMap<>();

    /**
     * Default timeout for SSE connections (4 hours).
     * Long enough to cover a typical match day.
     */
    private static final long SSE_TIMEOUT = 4 * 60 * 60 * 1000L;

    public SseEmitterService() {
        // No dependencies needed
    }

    /**
     * Simple JSON serialization for SSE events
     */
    private String toJson(MatchCompletionEvent event) {
        return String.format(
            "{\"type\":\"%s\",\"date\":\"%s\",\"matchesCompleted\":%d,\"timestamp\":\"%s\",\"message\":\"%s\"}",
            event.getType(),
            event.getDate(),
            event.getMatchesCompleted(),
            event.getTimestamp().toString(),
            event.getMessage()
        );
    }

    /**
     * Register a new SSE emitter for match completion events.
     *
     * @param date The date to listen for (YYYY-MM-DD format)
     * @return SseEmitter for the client to use
     */
    public SseEmitter subscribe(String date) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // Get or create emitter list for this date
        List<SseEmitter> dateEmitters = emitters.computeIfAbsent(date,
            k -> new CopyOnWriteArrayList<>());

        dateEmitters.add(emitter);

        log.info("[SSE] Client subscribed for date: {} (total clients: {})",
            date, dateEmitters.size());

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("{\"status\":\"connected\",\"date\":\"" + date + "\"}"));
        } catch (IOException e) {
            log.warn("[SSE] Failed to send initial event: {}", e.getMessage());
        }

        // Cleanup on completion/timeout/error
        emitter.onCompletion(() -> {
            dateEmitters.remove(emitter);
            log.debug("[SSE] Client disconnected for date: {}", date);
        });

        emitter.onTimeout(() -> {
            dateEmitters.remove(emitter);
            log.debug("[SSE] Client timeout for date: {}", date);
        });

        emitter.onError(e -> {
            dateEmitters.remove(emitter);
            log.debug("[SSE] Client error for date: {}", date);
        });

        // If completion was already sent for this date, send it immediately
        if (Boolean.TRUE.equals(completionSent.get(date))) {
            try {
                MatchCompletionEvent event = MatchCompletionEvent.allCompleted(date, 0);
                String json = toJson(event);
                emitter.send(SseEmitter.event()
                    .name("ALL_MATCHES_COMPLETED")
                    .data(json));
                emitter.complete();
                dateEmitters.remove(emitter);
                log.info("[SSE] Sent cached completion event to late subscriber for date: {}", date);
            } catch (IOException e) {
                log.warn("[SSE] Failed to send cached completion: {}", e.getMessage());
            }
        }

        return emitter;
    }

    /**
     * Broadcast match completion event to all subscribers for a date.
     * Called by backend when all matches for the day are completed.
     *
     * @param event The completion event to broadcast
     */
    public void broadcastCompletion(MatchCompletionEvent event) {
        String date = event.getDate();
        List<SseEmitter> dateEmitters = emitters.get(date);

        // Mark completion sent for this date
        completionSent.put(date, true);

        if (dateEmitters == null || dateEmitters.isEmpty()) {
            log.info("[SSE] No subscribers for date: {}, event cached for late subscribers", date);
            return;
        }

        log.info("[SSE] Broadcasting {} to {} clients for date: {}",
            event.getType(), dateEmitters.size(), date);

        try {
            String json = toJson(event);

            // Send to all subscribers
            for (SseEmitter emitter : dateEmitters) {
                try {
                    emitter.send(SseEmitter.event()
                        .name(event.getType())
                        .data(json));
                    // Complete the emitter after sending (one-time event)
                    emitter.complete();
                } catch (IOException e) {
                    log.debug("[SSE] Failed to send to emitter: {}", e.getMessage());
                }
            }

            // Clear emitters for this date
            dateEmitters.clear();

        } catch (Exception e) {
            log.error("[SSE] Failed to serialize event: {}", e.getMessage());
        }
    }

    /**
     * Broadcast a result update event (for corrections).
     */
    public void broadcastResultUpdate(String date, int matchCount) {
        MatchCompletionEvent event = MatchCompletionEvent.resultUpdated(date, matchCount);
        broadcastCompletion(event);
    }

    /**
     * Get count of active subscribers for a date.
     */
    public int getSubscriberCount(String date) {
        List<SseEmitter> dateEmitters = emitters.get(date);
        return dateEmitters != null ? dateEmitters.size() : 0;
    }

    /**
     * Check if completion was already sent for a date.
     */
    public boolean isCompletionSent(String date) {
        return Boolean.TRUE.equals(completionSent.get(date));
    }

    /**
     * Reset completion status for a date (for re-triggering).
     */
    public void resetCompletion(String date) {
        completionSent.remove(date);
        log.info("[SSE] Reset completion status for date: {}", date);
    }

    /**
     * Cleanup old date entries (called periodically).
     */
    public void cleanup() {
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        // Keep only today and yesterday, remove older entries
        emitters.keySet().removeIf(date ->
            !date.equals(today) && !date.equals(yesterday));
        completionSent.keySet().removeIf(date ->
            !date.equals(today) && !date.equals(yesterday));
    }
}

