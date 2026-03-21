package com.app.footballprediction.ratelimit;

import com.app.common.dto.ApiQuotaStatusDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ApiFootballRateLimiter.
 */
@DisplayName("ApiFootballRateLimiter Unit Tests")
class ApiFootballRateLimiterTest {

    private ApiFootballRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new ApiFootballRateLimiter(100, 20, 5, 27, true);
    }

    @Test
    @DisplayName("consumeWithinBudget: 100 tokens available, consume 10 → remaining 90")
    void consumeWithinBudget() {
        boolean result = limiter.tryConsume(10);

        assertThat(result).isTrue();
        assertThat(limiter.getDailyUsed()).isEqualTo(10);

        ApiQuotaStatusDTO status = limiter.getStatus();
        assertThat(status.getRemaining()).isEqualTo(90);
    }

    @Test
    @DisplayName("consumeExceedsBudget: consume 101 → tryConsume returns false")
    void consumeExceedsBudget() {
        boolean result = limiter.tryConsume(101);

        assertThat(result).isFalse();
        assertThat(limiter.getDailyUsed()).isEqualTo(0);
    }

    @Test
    @DisplayName("syncFromHeadersReducesBucket: local=80, apiHeader=55 → synced to 55")
    void syncFromHeadersReducesBucket() {
        // Consume 20, so local has 80 remaining
        limiter.tryConsume(20);
        assertThat(limiter.getDailyUsed()).isEqualTo(20);

        // API says only 55 remaining
        limiter.syncFromHeaders(55);

        ApiQuotaStatusDTO status = limiter.getStatus();
        assertThat(status.getRemaining()).isEqualTo(55);
        assertThat(status.getUsed()).isEqualTo(45);
    }

    @Test
    @DisplayName("canAffordInjuryWhenSufficient: 30 tokens left, INJURY needs 20 → true")
    void canAffordInjuryWhenSufficient() {
        // Consume 70, leaving 30
        limiter.tryConsume(70);

        assertThat(limiter.canAfford(BudgetCategory.INJURY)).isTrue();
    }

    @Test
    @DisplayName("canAffordInjuryWhenInsufficient: 15 tokens left, INJURY needs 20 → false")
    void canAffordInjuryWhenInsufficient() {
        // Consume 85, leaving 15
        limiter.tryConsume(85);

        assertThat(limiter.canAfford(BudgetCategory.INJURY)).isFalse();
    }

    @Test
    @DisplayName("disabledFlag: apifootball.enabled=false → tryConsume always returns false")
    void disabledFlag() {
        ApiFootballRateLimiter disabledLimiter = new ApiFootballRateLimiter(100, 20, 5, 27, false);

        assertThat(disabledLimiter.tryConsume(1)).isFalse();
        assertThat(disabledLimiter.canAfford(BudgetCategory.INJURY)).isFalse();
    }

    @Test
    @DisplayName("getStatus returns correct values")
    void getStatusReturnsCorrectValues() {
        limiter.tryConsume(25);

        ApiQuotaStatusDTO status = limiter.getStatus();
        assertThat(status.getDailyLimit()).isEqualTo(100);
        assertThat(status.getUsed()).isEqualTo(25);
        assertThat(status.getRemaining()).isEqualTo(75);
        assertThat(status.getInjuryBudget()).isEqualTo(20);
        assertThat(status.getFixtureBudget()).isEqualTo(5);
        assertThat(status.getReserveBudget()).isEqualTo(27);
        assertThat(status.isEnabled()).isTrue();
        assertThat(status.getResetsAt()).isNotBlank();
    }

    @Test
    @DisplayName("multiple small consumes track correctly")
    void multipleSmallConsumes() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryConsume(1)).isTrue();
        }
        assertThat(limiter.getDailyUsed()).isEqualTo(10);
    }

    @Test
    @DisplayName("consume exactly daily limit succeeds, next one fails")
    void consumeExactLimit() {
        assertThat(limiter.tryConsume(100)).isTrue();
        assertThat(limiter.tryConsume(1)).isFalse();
    }
}

