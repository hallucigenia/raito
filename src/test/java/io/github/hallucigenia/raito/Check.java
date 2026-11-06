// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.util.Arrays;
import java.util.Objects;

/** Minimal assertions for the dependency-free test runner. */
final class Check {
    interface Action { void run() throws Exception; }

    private Check() { }

    static void isTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void equal(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void bytes(byte[] expected, byte[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + Arrays.toString(expected)
                + " but was " + Arrays.toString(actual));
        }
    }

    static <T extends Throwable> T fails(Class<T> type, Action action, String message) {
        try {
            action.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) return type.cast(e);
            throw new AssertionError(message + ": expected " + type.getSimpleName() + " but got " + e, e);
        }
        throw new AssertionError(message + ": expected " + type.getSimpleName());
    }
}
