// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared state of the minimal Android framework stubs under {@code src/test/java/android}. They
 * mirror only the hidden-API shapes Raito reflects on, so capture code runs on a plain JVM. The
 * stubs are test-only and never reach the DEX jar. Call {@link #reset()} first in every test using them.
 */
public final class Stubs {
    /** Ordered log of observable side effects, such as "recycle screen" or "close buffer". */
    public static final List<String> EVENTS = new ArrayList<String>();

    private Stubs() { }

    public static void reset() {
        EVENTS.clear();
        android.graphics.Bitmap.reset();
        android.view.SurfaceControl.reset();
        android.hardware.display.DisplayManagerGlobal.reset();
    }
}
