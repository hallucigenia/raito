// SPDX-License-Identifier: Apache-2.0
package android.hardware;

import io.github.hallucigenia.raito.Stubs;

/** Test stub. */
public final class HardwareBuffer implements AutoCloseable {
    @Override
    public void close() {
        Stubs.EVENTS.add("close buffer");
    }
}
