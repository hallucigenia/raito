// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ForwarderTest {
    static final long MS = 1_000_000L;

    static final class FakeClock implements Forwarder.Clock {
        long now = 1_000 * MS;
        public long nanoTime() { return now; }
        public void sleepNanos(long nanos) { now += nanos; }
    }

    static class FakeCapture implements Capture {
        int frames;
        int failuresLeft;
        boolean closed;
        public Frame frame(int targetWidth) throws IOException {
            if (failuresLeft > 0) { failuresLeft--; throw new IOException("screenshot returned null"); }
            frames++;
            return new Frame(new byte[] {(byte) frames, 0, 0}, 1, 1);
        }
        public String describe() { return "fake"; }
        public void close() { closed = true; }
    }

    /** Hands out links that can refuse to connect or fail on send, like a restarting Hyperion. */
    static final class FakeHyperion implements FrameSink.Factory {
        int refusalsLeft;
        int sendFailuresLeft;
        int opened;
        Link last;

        final class Link implements FrameSink {
            boolean closed;
            public void send(Frame frame) throws IOException {
                if (sendFailuresLeft > 0) { sendFailuresLeft--; throw new IOException("Connection reset"); }
            }
            public void close() { closed = true; }
        }

        public FrameSink open() throws IOException {
            if (refusalsLeft > 0) { refusalsLeft--; throw new IOException("Connection refused"); }
            opened++;
            last = new Link();
            return last;
        }
    }

    static final class RecordingListener implements Forwarder.Listener {
        int acknowledged;
        final List<String> failures = new ArrayList<String>();
        final List<Boolean> awakeChanges = new ArrayList<Boolean>();
        public void frameSent(Frame frame, long captureNanos, long sendNanos, long atNanos) { acknowledged++; }
        public void failed(String stage, Exception error, Backoff backoff) { failures.add(stage); }
        public void awakeChanged(boolean awake) { awakeChanges.add(awake); }
        public void connected() { }
    }

    final FakeClock clock = new FakeClock();
    final FakeCapture capture = new FakeCapture();
    final FakeHyperion hyperion = new FakeHyperion();
    final RecordingListener listener = new RecordingListener();
    boolean awake = true;

    Forwarder forwarder(int fps) {
        return new Forwarder(160, fps, () -> capture, hyperion, () -> awake, clock, listener);
    }

    long step(Forwarder forwarder) {
        long delay = forwarder.step();
        if (delay > 0) clock.sleepNanos(delay);
        return delay;
    }

    public void keeps_retrying_while_hyperion_is_unreachable() {
        hyperion.refusalsLeft = 200;
        Forwarder forwarder = forwarder(12);
        long longestDelay = 0;
        for (int i = 0; i < 200; i++) longestDelay = Math.max(longestDelay, step(forwarder));
        Check.equal(200, listener.failures.size(), "every refusal is reported, none is fatal");
        Check.equal(30_000 * MS, longestDelay, "retry delay is capped");
        step(forwarder);
        Check.equal(1, listener.acknowledged, "frames resume once Hyperion returns");
    }

    public void backs_off_exponentially_between_retries() {
        hyperion.refusalsLeft = 3;
        Forwarder forwarder = forwarder(12);
        Check.equal(250 * MS, step(forwarder), "first retry");
        Check.equal(500 * MS, step(forwarder), "second retry");
        Check.equal(1000 * MS, step(forwarder), "third retry");
    }

    public void resets_backoff_after_an_acknowledged_frame() {
        hyperion.refusalsLeft = 3;
        Forwarder forwarder = forwarder(12);
        for (int i = 0; i < 4; i++) step(forwarder);
        Check.equal(1, listener.acknowledged, "frame sent after recovery");
        hyperion.sendFailuresLeft = 1;
        Check.equal(250 * MS, step(forwarder), "backoff starts over");
    }

    public void paces_frames_to_the_target_rate() {
        Forwarder forwarder = forwarder(10);
        Check.equal(100 * MS, step(forwarder), "fake capture and send take no time");
    }

    public void capture_failure_keeps_the_hyperion_link() {
        Forwarder forwarder = forwarder(12);
        step(forwarder);
        capture.failuresLeft = 1;
        step(forwarder);
        Check.equal("capture", listener.failures.get(0), "failure stage");
        Check.isTrue(!hyperion.last.closed, "Hyperion link stays registered");
        Check.isTrue(capture.closed, "capture is reopened after a failure");
        step(forwarder);
        Check.equal(1, hyperion.opened, "no re-registration");
        Check.equal(2, listener.acknowledged, "frames continue");
    }

    public void hyperion_failure_keeps_the_capture() {
        Forwarder forwarder = forwarder(12);
        step(forwarder);
        FakeHyperion.Link first = hyperion.last;
        hyperion.sendFailuresLeft = 1;
        step(forwarder);
        Check.equal("hyperion", listener.failures.get(0), "failure stage");
        Check.isTrue(first.closed, "broken link is closed");
        Check.isTrue(!capture.closed, "capture is kept");
        step(forwarder);
        Check.equal(2, hyperion.opened, "link re-registered");
        Check.equal(2, listener.acknowledged, "frames continue");
    }

    public void standby_releases_the_link_and_skips_capture() {
        Forwarder forwarder = forwarder(12);
        step(forwarder);
        awake = false;
        clock.sleepNanos(1000 * MS);
        Check.equal(1000 * MS, step(forwarder), "standby poll interval");
        Check.isTrue(hyperion.last.closed, "Hyperion link released in standby");
        Check.equal(1, capture.frames, "no capture in standby");
        Check.equal(false, listener.awakeChanges.get(0), "standby reported");
        step(forwarder);
        Check.equal(1, listener.awakeChanges.size(), "standby reported once");
        awake = true;
        step(forwarder);
        Check.equal(true, listener.awakeChanges.get(1), "wake reported");
        Check.equal(2, capture.frames, "capture resumes after wake");
    }

    public void power_check_failure_is_retried() {
        Forwarder forwarder = new Forwarder(160, 12, () -> capture, hyperion,
            () -> { throw new IllegalStateException("power service unavailable"); }, clock, listener);
        Check.equal(250 * MS, step(forwarder), "retried with backoff");
        Check.equal("power", listener.failures.get(0), "failure stage");
    }

    public void stale_link_after_capture_outage_retries_quickly() {
        Forwarder forwarder = forwarder(12);
        step(forwarder);
        capture.failuresLeft = 6;
        for (int i = 0; i < 6; i++) step(forwarder);
        hyperion.sendFailuresLeft = 1;
        Check.equal(250 * MS, step(forwarder), "Hyperion retry does not inherit the capture backoff");
        Check.equal("hyperion", listener.failures.get(6), "failure stage");
    }

    public void hyperion_outage_keeps_backing_off_while_capture_succeeds() {
        hyperion.refusalsLeft = 4;
        Forwarder forwarder = forwarder(12);
        for (int i = 0; i < 3; i++) step(forwarder);
        Check.equal(2000 * MS, step(forwarder), "capture success does not reset the Hyperion backoff");
    }

    public void wake_resets_backoff() {
        hyperion.refusalsLeft = 10;
        Forwarder forwarder = forwarder(12);
        for (int i = 0; i < 10; i++) step(forwarder);
        awake = false;
        step(forwarder);
        awake = true;
        hyperion.refusalsLeft = 1;
        Check.equal(250 * MS, step(forwarder), "first retry after wake is quick");
    }

    public void failure_caused_by_stop_is_not_reported() {
        final Forwarder[] holder = new Forwarder[1];
        final FakeCapture stopping = new FakeCapture() {
            public Frame frame(int targetWidth) throws IOException {
                holder[0].stop();
                throw new IOException("closed by shutdown");
            }
        };
        holder[0] = new Forwarder(160, 12, () -> stopping, hyperion, () -> awake, clock, listener);
        Check.equal(0L, holder[0].step(), "no retry delay once stopping");
        Check.equal(0, listener.failures.size(), "no RETRY report for a clean stop");
    }

    public void stop_closes_link_and_capture() {
        Forwarder forwarder = forwarder(12);
        step(forwarder);
        forwarder.stop();
        Check.isTrue(hyperion.last.closed, "link closed");
        Check.isTrue(capture.closed, "capture closed");
        Check.isTrue(!forwarder.isRunning(), "loop ends");
    }
}
