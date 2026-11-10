// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Backend names and their factories. */
final class Captures {
    static final String SURFACE = "surface";
    static final String SCREENCAP = "screencap";
    static final List<String> NAMES = Collections.unmodifiableList(Arrays.asList(SURFACE, SCREENCAP));

    private Captures() { }

    static Capture.Factory forBackend(String name) {
        if (SURFACE.equals(name)) {
            return new Capture.Factory() {
                public Capture open() throws Exception { return new SurfaceCapture(); }
            };
        }
        if (SCREENCAP.equals(name)) {
            return new Capture.Factory() {
                public Capture open() { return new ScreencapCapture(); }
            };
        }
        throw new IllegalArgumentException("Unknown backend " + name + "; expected one of " + NAMES);
    }

    /**
     * True when a backend cannot work on this firmware at all (missing hidden API, or a method
     * whose native or internal code is missing), as opposed to a transient failure reported by an
     * API call that exists.
     */
    static boolean isUnsupported(Exception error) {
        if (error instanceof InvocationTargetException) return error.getCause() instanceof LinkageError;
        return error instanceof UnsupportedOperationException || error instanceof ReflectiveOperationException;
    }
}
