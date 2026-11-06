// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.util.Map;

public final class FrameStatsTest {
    static Frame solid(int red, int green, int blue) {
        byte[] rgb = new byte[4 * 3];
        for (int i = 0; i < rgb.length; i += 3) {
            rgb[i] = (byte) red; rgb[i + 1] = (byte) green; rgb[i + 2] = (byte) blue;
        }
        return new Frame(rgb, 2, 2);
    }

    public void flags_all_black_capture() {
        FrameStats stats = new FrameStats();
        for (int i = 0; i < 5; i++) stats.add(solid(i, i, i), 1_000_000L);
        Check.equal("black", stats.verdict(), "near-black frames");
    }

    public void flags_frozen_capture() {
        FrameStats stats = new FrameStats();
        for (int i = 0; i < 5; i++) stats.add(solid(200, 100, 50), 1_000_000L);
        Check.equal("static", stats.verdict(), "identical frames");
    }

    public void accepts_changing_visible_frames() {
        FrameStats stats = new FrameStats();
        for (int i = 0; i < 5; i++) stats.add(solid(40 * i + 40, 80, 120), 1_000_000L);
        Check.equal("ok", stats.verdict(), "moving picture");
    }

    public void single_visible_frame_is_not_called_static() {
        FrameStats stats = new FrameStats();
        stats.add(solid(200, 100, 50), 1_000_000L);
        Check.equal("ok", stats.verdict(), "one frame cannot show motion");
    }

    public void summarizes_timing_and_brightness() {
        FrameStats stats = new FrameStats();
        stats.add(solid(255, 255, 255), 60_000_000L);
        stats.add(solid(0, 0, 0), 80_000_000L);
        Map<String, Object> summary = stats.summary();
        Check.equal(2, summary.get("frames"), "frame count");
        Check.equal(2, summary.get("distinct_frames"), "distinct frames");
        Check.equal(70.0, summary.get("capture_ms_avg"), "average capture time");
        Check.equal(80.0, summary.get("capture_ms_max"), "slowest capture");
        Check.equal(255, summary.get("max_luma"), "brightest pixel");
        Check.equal("2x2", summary.get("frame_size"), "frame size");
    }

    public void empty_stats_have_no_verdict() {
        Check.equal("no-frames", new FrameStats().verdict(), "nothing captured");
    }
}
