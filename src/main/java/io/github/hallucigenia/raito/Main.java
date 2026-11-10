// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.Closeable;
import java.util.Arrays;

/**
 * Entry point, started by the device launcher as the ordinary ADB shell user (never root):
 * {@code CLASSPATH=raito.jar app_process / io.github.hallucigenia.raito.Main <command> ...}.
 */
public final class Main {
    static final String VERSION = "0.2.0-dev";
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;
    static final int EXIT_ALREADY_RUNNING = 3;
    static final String USAGE = String.join("\n",
        "Usage: raito run --config FILE [--host H] [--port N] [--priority N] [--origin NAME]",
        "                 [--width N] [--fps N] [--backend surface|screencap]",
        "                 [--frame-duration-ms N] [--state-dir DIR]",
        "       raito probe [--backend all|surface|screencap] [--width N] [--frames N]",
        "       raito version",
        "(raito = CLASSPATH=raito.jar app_process / io.github.hallucigenia.raito.Main)");

    private Main() { }

    public static void main(String[] args) {
        int code;
        try {
            code = dispatch(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println(USAGE);
            code = EXIT_USAGE;
        } catch (AlreadyRunningException e) {
            System.err.println(e.getMessage());
            code = EXIT_ALREADY_RUNNING;
        } catch (Exception e) {
            System.err.println("error: " + Log.describe(e));
            code = EXIT_ERROR;
        }
        // Flush before exiting: native libraries some capture APIs load can crash the VM on exit.
        System.out.flush();
        System.err.flush();
        System.exit(code);
    }

    private static int dispatch(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Missing command");
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
            case "run": return run(rest);
            case "probe": System.out.println(Probe.run(rest)); return 0;
            case "version": System.out.println(VERSION); return 0;
            default: throw new IllegalArgumentException("Unknown command " + args[0]);
        }
    }

    private static int run(String[] args) throws Exception {
        Config config = Config.parse(args);
        StateDir state = new StateDir(config.stateDir);
        Closeable lock = state.lock();
        try {
            state.writePid();
            state.clearStatus();
            Capture.Factory captures = Captures.forBackend(config.backend);
            String source;
            // Open once up front: unsupported firmware fails at start, transient errors are left to the loop.
            try (Capture first = captures.open()) {
                source = first.describe();
            } catch (Exception e) {
                if (Captures.isUnsupported(e)) throw e;
                source = "unavailable, will retry (" + Log.describe(e) + ")";
            }
            final Forwarder forwarder = new Forwarder(config.width, config.fps, captures,
                HyperionClient.factory(config), new DisplayPower(), Forwarder.Clock.SYSTEM,
                new Reporter(state, config.fps));
            Runtime.getRuntime().addShutdownHook(new Thread("raito-shutdown") {
                public void run() { forwarder.stop(); }
            });
            Log.info("START version=" + VERSION + " " + config.describe() + " source=" + source);
            forwarder.run();
        } finally {
            lock.close();
        }
        return 0;
    }
}
