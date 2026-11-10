// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code probe}: tries each capture backend on this device and prints one JSON report, meant to be
 * pasted into a compatibility issue. It captures locally only and never connects to Hyperion.
 */
final class Probe {
    static final int DEFAULT_FRAMES = 30;
    static final int MAX_FRAMES = 300;
    static final long FRAME_GAP_MS = 100;
    static final String ALL = "all";
    static final String HINT = "Play moving video on the input you want to test (HDMI, an app, HDR) while probing. "
        + "'black' usually means that video layer cannot be captured; 'static' means frames did not change.";

    private Probe() { }

    static String run(String[] args) throws InterruptedException {
        Map<String, String> options = new LinkedHashMap<String, String>();
        options.put("backend", ALL);
        options.put("width", Config.DEFAULTS.get("width"));
        options.put("frames", Integer.toString(DEFAULT_FRAMES));
        Map<String, String> given = Options.parse(args);
        for (String key : given.keySet()) {
            if (!options.containsKey(key)) throw new IllegalArgumentException("Unknown probe option " + key);
        }
        options.putAll(given);
        int width = Options.integer(options, "width", Config.MIN_WIDTH, Config.MAX_WIDTH);
        int frames = Options.integer(options, "frames", 1, MAX_FRAMES);

        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("tool", "raito");
        report.put("version", Main.VERSION);
        report.put("device", DeviceInfo.describe());
        report.put("screen_awake", screenAwake());
        List<Object> results = new ArrayList<Object>();
        for (String backend : backends(options.get("backend"))) results.add(probe(backend, width, frames));
        report.put("results", results);
        report.put("hint", HINT);
        return Json.write(report);
    }

    private static List<String> backends(String choice) {
        if (ALL.equals(choice)) return Captures.NAMES;
        if (Captures.NAMES.contains(choice)) return Collections.singletonList(choice);
        throw new IllegalArgumentException("backend must be all or one of " + Captures.NAMES + ", got " + choice);
    }

    private static Object screenAwake() {
        try {
            return new DisplayPower().awake();
        } catch (Exception e) {
            return "unknown: " + Log.describe(e);
        }
    }

    private static Map<String, Object> probe(String backend, int width, int frames) throws InterruptedException {
        FrameStats stats = new FrameStats();
        String source = null;
        String error = null;
        try (Capture capture = Captures.forBackend(backend).open()) {
            // Read before the first frame too, so a failing capture still reports the API it chose.
            source = capture.describe();
            for (int i = 0; i < frames; i++) {
                long started = System.nanoTime();
                Frame frame = capture.frame(width);
                stats.add(frame, System.nanoTime() - started);
                Thread.sleep(FRAME_GAP_MS);
            }
            source = capture.describe();
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            error = Log.describe(e);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("backend", backend);
        result.put("ok", error == null);
        if (error != null) result.put("error", error);
        if (source != null) result.put("source", source);
        result.putAll(stats.summary());
        if (error != null) result.put("verdict", "error");
        return result;
    }
}
