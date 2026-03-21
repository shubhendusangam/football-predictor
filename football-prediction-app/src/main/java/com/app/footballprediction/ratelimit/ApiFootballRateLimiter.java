package com.app.footballprediction.ratelimit;

import com.app.common.dto.ApiQuotaStatusDTO;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory rate limiter for API-Football using Bucket4j.
 * Tracks daily quota (100 calls/day free tier) with budget categories.
 * <p>
 * No Redis required — uses a single local Bucket plus an AtomicInteger
 * that is reset at midnight UTC via a @Scheduled method.
 */
@Component
@Slf4j
public class ApiFootballRateLimiter {

    private final Bucket bucket;
    private final AtomicInteger dailyUsed = new AtomicInteger(0);
    private final AtomicReference<LocalDate> lastResetDate = new AtomicReference<>(LocalDate.now(ZoneOffset.UTC));

    private final int dailyLimit;
    private final int injuryBudget;
    private final int fixtureBudget;
    private final int reserveBudget;
    private final boolean enabled;

    public ApiFootballRateLimiter(
            @Value("${apifootball.api.daily-limit:100}") int dailyLimit,
            @Value("${apifootball.api.budget.injury:20}") int injuryBudget,
            @Value("${apifootball.api.budget.fixtures:5}") int fixtureBudget,
            @Value("${apifootball.api.budget.reserve:27}") int reserveBudget,
            @Value("${apifootball.api.enabled:true}") boolean enabled) {

        this.dailyLimit = dailyLimit;
        this.injuryBudget = injuryBudget;
        this.fixtureBudget = fixtureBudget;
        this.reserveBudget = reserveBudget;
        this.enabled = enabled;

        this.bucket = Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(dailyLimit)
                        .refillIntervally(dailyLimit, Duration.ofDays(1))
                        .build())
                .build();

        log.info("API-Football rate limiter initialised: daily-limit={}, injury={}, fixtures={}, reserve={}, enabled={}",
                dailyLimit, injuryBudget, fixtureBudget, reserveBudget, enabled);
    }

    /**
     * Attempt to consume {@code tokens} from the daily quota.
     *
     * @return true if tokens were consumed, false if quota exhausted or disabled
     */
    public boolean tryConsume(int tokens) {
        if (!enabled) {
            log.debug("API-Football disabled — tryConsume({}) rejected", tokens);
            return false;
        }

        resetIfNewDay();

        if (bucket.tryConsume(tokens)) {
            int used = dailyUsed.addAndGet(tokens);
            log.info("API-Football quota consumed: {}. Used today: {}/{}", tokens, used, dailyLimit);
            return true;
        }

        log.warn("API-Football daily quota exhausted — tryConsume({}) rejected. Used: {}/{}",
                tokens, dailyUsed.get(), dailyLimit);
        return false;
    }

    /**
     * Check whether the remaining quota can afford the specified budget category.
     */
    public boolean canAfford(BudgetCategory category) {
        if (!enabled) return false;

        resetIfNewDay();

        int remaining = dailyLimit - dailyUsed.get();
        int needed = switch (category) {
            case INJURY -> injuryBudget;
            case FIXTURES -> fixtureBudget;
            case STANDINGS -> fixtureBudget; // same budget as fixtures
            case RESERVE -> reserveBudget;
        };

        boolean affordable = remaining >= needed;
        log.debug("canAfford({}) → remaining={}, needed={}, result={}", category, remaining, needed, affordable);
        return affordable;
    }

    /**
     * Get current quota status for admin monitoring.
     */
    public ApiQuotaStatusDTO getStatus() {
        resetIfNewDay();
        int used = dailyUsed.get();

        ZonedDateTime nextMidnight = ZonedDateTime.now(ZoneOffset.UTC)
                .plusDays(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        return ApiQuotaStatusDTO.builder()
                .dailyLimit(dailyLimit)
                .used(used)
                .remaining(dailyLimit - used)
                .resetsAt(nextMidnight.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .injuryBudget(injuryBudget)
                .fixtureBudget(fixtureBudget)
                .reserveBudget(reserveBudget)
                .isEnabled(enabled)
                .build();
    }

    /**
     * Sync local tracker with the real x-ratelimit-requests-remaining header from API-Football.
     * The API header is the source of truth — if it reports fewer remaining calls than our
     * local bucket, we force-sync downwards.
     */
    public void syncFromHeaders(int apiRemaining) {
        resetIfNewDay();

        long localAvailable = bucket.getAvailableTokens();
        if (apiRemaining < localAvailable) {
            log.info("Syncing API-Football quota from headers: local={}, api={} → forcing to {}",
                    localAvailable, apiRemaining, apiRemaining);
            // Consume the difference to bring the bucket down
            long diff = localAvailable - apiRemaining;
            bucket.tryConsume(diff);
            dailyUsed.set(dailyLimit - apiRemaining);
        }
    }

    /**
     * Reset daily counters at midnight UTC.
     * Called both via @Scheduled and lazily on each call to handle restarts.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void resetDailyCounters() {
        dailyUsed.set(0);
        lastResetDate.set(LocalDate.now(ZoneOffset.UTC));
        log.info("API-Football daily quota reset. Available: {}", dailyLimit);
    }

    /**
     * Gracefully handle app restarts: if today differs from the last tracked reset date,
     * reset counters.
     */
    private void resetIfNewDay() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate lastReset = lastResetDate.get();
        if (!today.equals(lastReset)) {
            if (lastResetDate.compareAndSet(lastReset, today)) {
                dailyUsed.set(0);
                log.info("API-Football quota reset on new day ({}). Available: {}", today, dailyLimit);
            }
        }
    }

    // ── Package-private accessors for testing ────────────────────────

    int getDailyUsed() {
        return dailyUsed.get();
    }

    int getDailyLimit() {
        return dailyLimit;
    }

    boolean isEnabled() {
        return enabled;
    }
}



