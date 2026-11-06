// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class ConfigTest {
    public void applies_defaults_when_only_host_is_given() {
        Config config = Config.parse(new String[] {"--host", "192.168.1.10"});
        Check.equal("192.168.1.10", config.host, "host");
        Check.equal(19400, config.port, "default FlatBuffers port");
        Check.equal(150, config.priority, "default priority");
        Check.equal(160, config.width, "default width");
        Check.equal(12, config.fps, "default fps");
        Check.equal("surface", config.backend, "default backend");
        Check.equal("Raito", config.origin, "default origin");
        Check.equal(1500, config.frameDurationMs, "default frame duration");
        Check.equal(Config.DEFAULT_STATE_DIR, config.stateDir, "default state dir");
    }

    public void flags_override_config_file_values() throws Exception {
        File file = File.createTempFile("grabber", ".conf");
        file.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("# comment\nhost=10.0.0.2\nfps=10\norigin=Living room\n".getBytes(StandardCharsets.UTF_8));
        }
        Config config = Config.parse(new String[] {"--config", file.getPath(), "--fps", "15"});
        Check.equal("10.0.0.2", config.host, "host from file");
        Check.equal(15, config.fps, "flag wins over file");
        Check.equal("Living room", config.origin, "origin from file");
    }

    public void maps_dashed_flags_to_underscored_keys() {
        Config config = Config.parse(new String[] {"--host", "h", "--frame-duration-ms", "2000",
            "--state-dir", "/tmp/grabber"});
        Check.equal(2000, config.frameDurationMs, "frame duration flag");
        Check.equal("/tmp/grabber", config.stateDir, "state dir flag");
    }

    public void rejects_missing_host() {
        IllegalArgumentException e = Check.fails(IllegalArgumentException.class,
            () -> Config.parse(new String[0]), "missing host");
        Check.isTrue(e.getMessage().contains("host"), "message names the key");
    }

    public void rejects_values_out_of_range() {
        Check.fails(IllegalArgumentException.class,
            () -> Config.parse(new String[] {"--host", "h", "--port", "70000"}), "port");
        Check.fails(IllegalArgumentException.class,
            () -> Config.parse(new String[] {"--host", "h", "--priority", "50"}), "priority below Hyperion range");
        Check.fails(IllegalArgumentException.class,
            () -> Config.parse(new String[] {"--host", "h", "--fps", "0"}), "fps");
        Check.fails(IllegalArgumentException.class,
            () -> Config.parse(new String[] {"--host", "h", "--width", "abc"}), "non-numeric width");
    }

    public void rejects_unknown_keys_and_backends() {
        IllegalArgumentException e = Check.fails(IllegalArgumentException.class,
            () -> Config.parse(new String[] {"--host", "h", "--pritority", "150"}), "typo");
        Check.isTrue(e.getMessage().contains("pritority"), "message names the unknown key");
        Check.fails(IllegalArgumentException.class,
            () -> Config.parse(new String[] {"--host", "h", "--backend", "mediaprojection"}), "backend");
    }

    public void rejects_flag_without_value() {
        Check.fails(IllegalArgumentException.class,
            () -> Config.parse(new String[] {"--host"}), "dangling flag");
    }

    public void rejects_missing_config_file() {
        Check.fails(IllegalArgumentException.class,
            () -> Config.parse(new String[] {"--config", "/nonexistent/raito.conf"}), "missing file");
    }
}
