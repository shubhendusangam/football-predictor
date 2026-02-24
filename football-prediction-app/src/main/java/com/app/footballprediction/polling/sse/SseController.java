package com.app.footballprediction.polling.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;

/**
 * Controller for Server-Sent Events (SSE) endpoints.
 * Provides real-time match completion notifications without polling.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class SseController {

    private final SseEmitterService sseEmitterService;

    /**
     * Subscribe to match completion events for today.
     *
     * <p>Client connects and waits for ONE event:
     * <ul>
     *   <li>ALL_MATCHES_COMPLETED - when all matches finish</li>
     *   <li>MATCH_RESULT_UPDATED - when a result is corrected</li>
     * </ul>
     *
     * <p>After receiving the event, connection is closed automatically.
     *
     * @return SseEmitter for the client to listen on
     */
    @GetMapping(value = "/match-completion", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToMatchCompletion() {
        String today = LocalDate.now().toString();
        log.info("[SSE] New subscription request for match completion (date: {})", today);
        return sseEmitterService.subscribe(today);
    }

    /**
     * Subscribe to match completion events for a specific date.
     *
     * @param date The date in YYYY-MM-DD format
     * @return SseEmitter for the client to listen on
     */
    @GetMapping(value = "/match-completion/{date}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToMatchCompletionForDate(@PathVariable String date) {
        log.info("[SSE] New subscription request for match completion (date: {})", date);
        return sseEmitterService.subscribe(date);
    }

    /**
     * Get SSE connection status.
     */
    @GetMapping("/status")
    public java.util.Map<String, Object> getStatus() {
        String today = LocalDate.now().toString();
        return java.util.Map.of(
            "date", today,
            "subscribers", sseEmitterService.getSubscriberCount(today),
            "completionSent", sseEmitterService.isCompletionSent(today)
        );
    }
}

