// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.Closeable;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Capture-and-send loop. Failures never end the loop: capture errors reopen the capture,
 * Hyperion errors reopen the connection, and each stage backs off on its own schedule, reset
 * when that stage succeeds again or the screen wakes. While the screen is off the connection
 * is released and nothing is captured.
 */
final class Forwarder {
    static final long SECOND_NANOS = 1_000_000_000L;
    static final long POWER_CHECK_INTERVAL_NANOS = SECOND_NANOS;
    static final long STANDBY_POLL_NANOS = SECOND_NANOS;

    enum Stage {
        POWER("power"), CAPTURE("capture"), HYPERION("hyperion");

        final String label;

        Stage(String label) { this.label = label; }
    }

    interface Power {
        boolean awake() throws Exception;
    }

    interface Clock {
        long nanoTime();

        void sleepNanos(long nanos) throws InterruptedException;

        Clock SYSTEM = new Clock() {
            public long nanoTime() { return System.nanoTime(); }
            public void sleepNanos(long nanos) throws InterruptedException { TimeUnit.NANOSECONDS.sleep(nanos); }
        };
    }

    interface Listener {
        void connected();

        /** Called only after Hyperion acknowledged the frame. */
        void frameSent(Frame frame, long captureNanos, long sendNanos, long atNanos);

        void failed(String stage, Exception error, Backoff backoff);

        void awakeChanged(boolean awake);
    }

    private final int width;
    private final long frameIntervalNanos;
    private final Capture.Factory captures;
    private final FrameSink.Factory sinks;
    private final Power power;
    private final Clock clock;
    private final Listener listener;
    private final Map<Stage, Backoff> backoffs = new EnumMap<Stage, Backoff>(Stage.class);

    private volatile boolean running = true;
    private volatile Capture capture;
    private volatile FrameSink sink;
    private boolean awake = true;
    private boolean hasPowerReading;
    private long lastPowerCheck;

    Forwarder(int width, int fps, Capture.Factory captures, FrameSink.Factory sinks, Power power,
              Clock clock, Listener listener) {
        this.width = width;
        this.frameIntervalNanos = SECOND_NANOS / fps;
        this.captures = captures;
        this.sinks = sinks;
        this.power = power;
        this.clock = clock;
        this.listener = listener;
        resetBackoffs();
    }

    void run() throws InterruptedException {
        try {
            while (running) {
                long delay = step();
                if (delay > 0 && running) clock.sleepNanos(delay);
            }
        } finally {
            release();
        }
    }

    /** Safe to call from a shutdown hook; the loop exits after its current step. */
    void stop() {
        running = false;
        release();
    }

    boolean isRunning() {
        return running;
    }

    /** Runs one iteration and returns how long to wait before the next one. */
    long step() {
        long started = clock.nanoTime();
        boolean screenOn;
        try {
            screenOn = checkAwake(started);
        } catch (Exception e) {
            return failed(Stage.POWER, e);
        }
        succeeded(Stage.POWER);
        if (!screenOn) {
            release();
            return STANDBY_POLL_NANOS;
        }
        Frame frame;
        long captured;
        try {
            if (capture == null) capture = captures.open();
            frame = capture.frame(width);
            captured = clock.nanoTime();
        } catch (Exception e) {
            capture = closeQuietly(capture);
            return failed(Stage.CAPTURE, e);
        }
        succeeded(Stage.CAPTURE);
        try {
            if (sink == null) {
                sink = sinks.open();
                listener.connected();
            }
            sink.send(frame);
        } catch (Exception e) {
            sink = closeQuietly(sink);
            return failed(Stage.HYPERION, e);
        }
        succeeded(Stage.HYPERION);
        long sent = clock.nanoTime();
        listener.frameSent(frame, captured - started, sent - captured, sent);
        return Math.max(0, frameIntervalNanos - (sent - started));
    }

    private boolean checkAwake(long now) throws Exception {
        if (hasPowerReading && now - lastPowerCheck < POWER_CHECK_INTERVAL_NANOS) return awake;
        boolean current = power.awake();
        hasPowerReading = true;
        lastPowerCheck = now;
        if (current != awake) {
            awake = current;
            if (current) resetBackoffs();
            listener.awakeChanged(current);
        }
        return current;
    }

    private long failed(Stage stage, Exception error) {
        // During shutdown, stop() closes resources under a running step; that is not a failure.
        if (!running) return 0;
        Backoff next = backoffs.get(stage).afterFailure();
        backoffs.put(stage, next);
        listener.failed(stage.label, error, next);
        return TimeUnit.MILLISECONDS.toNanos(next.delayMs());
    }

    private void succeeded(Stage stage) {
        backoffs.put(stage, Backoff.initial());
    }

    private void resetBackoffs() {
        for (Stage stage : Stage.values()) backoffs.put(stage, Backoff.initial());
    }

    private void release() {
        sink = closeQuietly(sink);
        capture = closeQuietly(capture);
    }

    /** Closes a resource that is being abandoned after a failure; always returns null. */
    private static <T extends Closeable> T closeQuietly(T resource) {
        if (resource == null) return null;
        try {
            resource.close();
        } catch (IOException e) {
            Log.warn("close failed: " + Log.describe(e));
        }
        return null;
    }
}
