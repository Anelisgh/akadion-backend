package com.example.akadion.auth.service;

import com.example.akadion.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterServiceTest {

    private final RateLimiterService rateLimiterService = new RateLimiterService();

    @Test
    void allowsRequestsUpToTheLimit() {
        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> rateLimiterService.verificaLimita("cheie-1", 5, Duration.ofMinutes(1)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void throwsOnceLimitIsExceeded() {
        for (int i = 0; i < 5; i++) {
            rateLimiterService.verificaLimita("cheie-2", 5, Duration.ofMinutes(1));
        }

        Duration fereastra = Duration.ofMinutes(1);
        assertThatThrownBy(() -> rateLimiterService.verificaLimita("cheie-2", 5, fereastra))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void keysAreIndependentFromEachOther() {
        for (int i = 0; i < 5; i++) {
            rateLimiterService.verificaLimita("student-aky:1", 5, Duration.ofMinutes(1));
        }

        // O cheie diferită (alt student, sau alt bucket precum "conversatie:") nu trebuie afectată.
        assertThatCode(() -> rateLimiterService.verificaLimita("student-aky:2", 5, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> rateLimiterService.verificaLimita("conversatie:1", 5, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }
}
