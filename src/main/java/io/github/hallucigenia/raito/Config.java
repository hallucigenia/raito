// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Validated settings for the {@code run} command: defaults, then the config file, then flags.
 * The state directory is a flag only ({@code --state-dir}): the device launcher owns it and
 * always passes it, so a config file value could never take effect.
 */
final class Config {
    static final String DEFAULT_STATE_DIR = "/data/local/tmp/raito";
    static final String CONFIG_FLAG = "config";
    static final String STATE_DIR_FLAG = "state_dir";
    /** Hyperion 2.x accepts FlatBuffers clients only in this priority range. */
    static final int MIN_PRIORITY = 100;
    static final int MAX_PRIORITY = 199;
    static final int MIN_WIDTH = 16;
    static final int MAX_WIDTH = 640;
    static final int MAX_FPS = 30;
    static final int MIN_FRAME_DURATION_MS = 100;
    static final int MAX_FRAME_DURATION_MS = 10_000;
    static final int MAX_ORIGIN_LENGTH = 64;

    static final Map<String, String> DEFAULTS;

    static {
        Map<String, String> defaults = new LinkedHashMap<String, String>();
        defaults.put("host", "");
        defaults.put("port", "19400");
        defaults.put("priority", "150");
        defaults.put("origin", "Raito");
        defaults.put("width", "160");
        defaults.put("fps", "12");
        defaults.put("backend", Captures.SURFACE);
        defaults.put("frame_duration_ms", "1500");
        DEFAULTS = Collections.unmodifiableMap(defaults);
    }

    final String host;
    final int port;
    final int priority;
    final String origin;
    final int width;
    final int fps;
    final String backend;
    final int frameDurationMs;
    final String stateDir;

    private Config(Map<String, String> values, String stateDir) {
        for (String key : values.keySet()) {
            if (STATE_DIR_FLAG.equals(key)) {
                throw new IllegalArgumentException("state_dir is not a config file setting; the launcher passes --state-dir");
            }
            if (!DEFAULTS.containsKey(key)) throw new IllegalArgumentException("Unknown setting " + key);
        }
        host = text(values, "host", 253);
        if (host.indexOf(' ') >= 0) throw new IllegalArgumentException("host must not contain spaces");
        port = Options.integer(values, "port", 1, 65535);
        priority = Options.integer(values, "priority", MIN_PRIORITY, MAX_PRIORITY);
        origin = text(values, "origin", MAX_ORIGIN_LENGTH);
        width = Options.integer(values, "width", MIN_WIDTH, MAX_WIDTH);
        fps = Options.integer(values, "fps", 1, MAX_FPS);
        backend = values.get("backend");
        if (!Captures.NAMES.contains(backend)) {
            throw new IllegalArgumentException("backend must be one of " + Captures.NAMES + ", got " + backend);
        }
        frameDurationMs = Options.integer(values, "frame_duration_ms", MIN_FRAME_DURATION_MS, MAX_FRAME_DURATION_MS);
        this.stateDir = stateDir;
    }

    static Config parse(String[] args) {
        Map<String, String> flags = Options.parse(args);
        Map<String, String> values = new HashMap<String, String>(DEFAULTS);
        String file = flags.get(CONFIG_FLAG);
        if (file != null) values.putAll(load(file));
        for (Map.Entry<String, String> flag : flags.entrySet()) {
            String key = flag.getKey();
            if (!key.equals(CONFIG_FLAG) && !key.equals(STATE_DIR_FLAG)) values.put(key, flag.getValue());
        }
        return new Config(values, stateDir(flags));
    }

    private static String stateDir(Map<String, String> flags) {
        if (!flags.containsKey(STATE_DIR_FLAG)) return DEFAULT_STATE_DIR;
        String dir = text(flags, STATE_DIR_FLAG, 4096);
        if (!dir.startsWith("/")) throw new IllegalArgumentException("--state-dir must be an absolute path");
        return dir;
    }

    String describe() {
        return "host=" + host + " port=" + port + " priority=" + priority + " origin=" + origin
            + " width=" + width + " fps=" + fps + " backend=" + backend + " frame_duration_ms=" + frameDurationMs;
    }

    private static Map<String, String> load(String path) {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read config file " + path + ": " + e.getMessage());
        }
        Map<String, String> values = new HashMap<String, String>();
        for (String key : properties.stringPropertyNames()) values.put(key, properties.getProperty(key).trim());
        return values;
    }

    private static String text(Map<String, String> values, String key, int maxLength) {
        String value = values.get(key);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(key + " is required");
        value = value.trim();
        if (value.length() > maxLength) throw new IllegalArgumentException(key + " is longer than " + maxLength);
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException(key + " contains control characters");
            }
        }
        return value;
    }
}
