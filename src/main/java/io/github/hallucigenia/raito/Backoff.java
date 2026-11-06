// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

/** Immutable retry state: 250 ms doubling to a 30 s ceiling, never giving up. */
final class Backoff {
    static final long INITIAL_DELAY_MS = 250;
    static final long MAX_DELAY_MS = 30_000;
    /** The first failures are always logged; after that only every twentieth, to bound log growth. */
    static final int ALWAYS_LOGGED_FAILURES = 3;
    static final int LOG_EVERY_FAILURES = 20;

    private static final Backoff INITIAL = new Backoff(0, 0);

    private final int failures;
    private final long delayMs;

    private Backoff(int failures, long delayMs) {
        this.failures = failures;
        this.delayMs = delayMs;
    }

    static Backoff initial() {
        return INITIAL;
    }

    Backoff afterFailure() {
        long next = failures == 0 ? INITIAL_DELAY_MS : Math.min(MAX_DELAY_MS, delayMs * 2);
        return new Backoff(failures + 1, next);
    }

    long delayMs() {
        return delayMs;
    }

    int failures() {
        return failures;
    }

    boolean shouldLog() {
        return failures <= ALWAYS_LOGGED_FAILURES || failures % LOG_EVERY_FAILURES == 0;
    }
}
