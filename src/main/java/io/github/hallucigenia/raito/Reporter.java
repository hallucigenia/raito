// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.IOException;
import java.util.Locale;

/**
 * Production listener: rewrites the status file every few seconds while frames are acknowledged,
 * and keeps the log small (one frame summary per minute, sampled retry messages).
 */
final class Reporter implements Forwarder.Listener {
    static final long STATUS_INTERVAL_NANOS = 5 * Forwarder.SECOND_NANOS;
    static final long LOG_INTERVAL_NANOS = 60 * Forwarder.SECOND_NANOS;

    private final StateDir state;
    private final int targetFps;
    private long totalFrames;
    private boolean windowOpen;
    private long windowStart;
    private long windowFrames;
    private long windowCaptureNanos;
    private long windowSendNanos;
    private boolean hasLogged;
    private long lastLog;

    Reporter(StateDir state, int targetFps) {
        this.state = state;
        this.targetFps = targetFps;
    }

    public void connected() {
        Log.info("CONNECTED to Hyperion");
    }

    public void frameSent(Frame frame, long captureNanos, long sendNanos, long atNanos) {
        totalFrames++;
        if (!windowOpen) {
            // Publish at once so health turns green on the first acknowledged frame.
            publish(frame, 0, 0, 0, atNanos);
            openWindow(atNanos);
            return;
        }
        windowFrames++;
        windowCaptureNanos += captureNanos;
        windowSendNanos += sendNanos;
        long elapsed = atNanos - windowStart;
        if (elapsed < STATUS_INTERVAL_NANOS) return;
        publish(frame, windowFrames * 1e9 / elapsed,
            windowCaptureNanos / 1e6 / windowFrames, windowSendNanos / 1e6 / windowFrames, atNanos);
        openWindow(atNanos);
    }

    public void failed(String stage, Exception error, Backoff backoff) {
        windowOpen = false;
        if (backoff.shouldLog()) {
            Log.warn("RETRY stage=" + stage + " failures=" + backoff.failures() + " next_ms=" + backoff.delayMs()
                + " error=" + Log.describe(error));
        }
    }

    public void awakeChanged(boolean awake) {
        windowOpen = false;
        Log.info(awake ? "AWAKE resuming capture" : "STANDBY pausing capture and releasing Hyperion");
    }

    private void openWindow(long atNanos) {
        windowOpen = true;
        windowStart = atNanos;
        windowFrames = 0;
        windowCaptureNanos = 0;
        windowSendNanos = 0;
    }

    private void publish(Frame frame, double fps, double captureMs, double sendMs, long atNanos) {
        String status = String.format(Locale.ROOT,
            "frames=%d%nfps=%.2f%ncapture_ms=%.1f%nsend_ms=%.1f%nwidth=%d%nheight=%d%ntarget_fps=%d%n",
            totalFrames, fps, captureMs, sendMs, frame.width, frame.height, targetFps);
        try {
            state.writeStatus(status);
        } catch (IOException e) {
            Log.warn("Cannot write status: " + Log.describe(e));
        }
        if (!hasLogged || atNanos - lastLog >= LOG_INTERVAL_NANOS) {
            Log.info(String.format(Locale.ROOT, "FRAME total=%d size=%s fps=%.2f capture_ms=%.1f send_ms=%.1f",
                totalFrames, frame.size(), fps, captureMs, sendMs));
            hasLogged = true;
            lastLog = atNanos;
        }
    }
}
