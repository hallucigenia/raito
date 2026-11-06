// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.Closeable;
import java.io.IOException;

/** Destination that acknowledges each frame, such as a registered Hyperion connection. */
interface FrameSink extends Closeable {
    void send(Frame frame) throws IOException;

    interface Factory {
        FrameSink open() throws IOException;
    }
}
