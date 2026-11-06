// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.Closeable;

/** A screen capture backend. Implementations run as the ADB shell user and use hidden APIs by reflection. */
interface Capture extends Closeable {
    /** Captures the current screen scaled to at most {@code targetWidth} pixels wide. */
    Frame frame(int targetWidth) throws Exception;

    /** Short human-readable description of the source, for logs and probe output. */
    String describe();

    interface Factory {
        Capture open() throws Exception;
    }
}
