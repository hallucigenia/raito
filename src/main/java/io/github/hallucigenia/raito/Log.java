// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

/** Timestamped event log on stdout; the device launcher redirects it to a file. */
final class Log {
    private Log() { }

    static void info(String message) { print("INFO", message); }

    static void warn(String message) { print("WARN", message); }

    /** Error text with reflection wrappers removed, so the real cause reaches the user. */
    static String describe(Throwable error) {
        Throwable cause = error;
        while (cause instanceof InvocationTargetException && cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static synchronized void print(String level, String message) {
        System.out.println(String.format(Locale.ROOT, "%tFT%<tT%<tz %s %s", System.currentTimeMillis(), level, message));
    }
}
