// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Probe statistics that tell a working capture from a black or frozen one. */
final class FrameStats {
    /** Video black is luma 16; anything at or below this everywhere counts as a black capture. */
    static final int BLACK_LUMA_MAX = 20;

    private final Set<Integer> hashes = new HashSet<Integer>();
    private int frames;
    private long captureNanosTotal;
    private long captureNanosMax;
    private int maxLuma;
    private double meanLumaTotal;
    private String frameSize = "";

    void add(Frame frame, long captureNanos) {
        frames++;
        hashes.add(Arrays.hashCode(frame.rgb));
        captureNanosTotal += captureNanos;
        captureNanosMax = Math.max(captureNanosMax, captureNanos);
        frameSize = frame.size();
        long lumaSum = 0;
        for (int i = 0; i < frame.rgb.length; i += 3) {
            int luma = luma(frame.rgb[i] & 0xff, frame.rgb[i + 1] & 0xff, frame.rgb[i + 2] & 0xff);
            lumaSum += luma;
            maxLuma = Math.max(maxLuma, luma);
        }
        meanLumaTotal += (double) lumaSum / (frame.width * frame.height);
    }

    String verdict() {
        if (frames == 0) return "no-frames";
        if (maxLuma <= BLACK_LUMA_MAX) return "black";
        if (frames > 1 && hashes.size() == 1) return "static";
        return "ok";
    }

    Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("verdict", verdict());
        summary.put("frames", frames);
        if (frames == 0) return summary;
        double averageMs = captureNanosTotal / 1e6 / frames;
        summary.put("distinct_frames", hashes.size());
        summary.put("frame_size", frameSize);
        summary.put("capture_ms_avg", round1(averageMs));
        summary.put("capture_ms_max", round1(captureNanosMax / 1e6));
        summary.put("max_fps_estimate", averageMs > 0 ? round1(1000 / averageMs) : null);
        summary.put("mean_luma", round1(meanLumaTotal / frames));
        summary.put("max_luma", maxLuma);
        return summary;
    }

    private static int luma(int red, int green, int blue) {
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
