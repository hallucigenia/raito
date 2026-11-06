// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

public final class BackoffTest {
    public void doubles_delay_after_each_failure() {
        Backoff backoff = Backoff.initial().afterFailure();
        Check.equal(250L, backoff.delayMs(), "first retry delay");
        backoff = backoff.afterFailure();
        Check.equal(500L, backoff.delayMs(), "second retry delay");
        backoff = backoff.afterFailure();
        Check.equal(1000L, backoff.delayMs(), "third retry delay");
        Check.equal(3, backoff.failures(), "failure count");
    }

    public void caps_delay_at_thirty_seconds() {
        Backoff backoff = Backoff.initial();
        for (int i = 0; i < 100; i++) backoff = backoff.afterFailure();
        Check.equal(30_000L, backoff.delayMs(), "capped delay");
        Check.equal(100, backoff.failures(), "failures keep counting after the cap");
    }

    public void is_immutable() {
        Backoff first = Backoff.initial();
        first.afterFailure();
        Check.equal(0, first.failures(), "original value is unchanged");
    }

    public void logs_first_failures_then_samples() {
        Backoff backoff = Backoff.initial();
        int logged = 0;
        for (int i = 0; i < 100; i++) {
            backoff = backoff.afterFailure();
            if (backoff.shouldLog()) logged++;
        }
        Check.equal(3 + 5, logged, "first three plus every twentieth failure");
    }
}
