// SPDX-License-Identifier: Apache-2.0
package android.hardware.display;

import android.view.DisplayAddress;
import android.view.DisplayInfo;

/** Test stub: a 1920×1080 built-in display with physical id 42 unless a test changes it. */
public final class DisplayManagerGlobal {
    public static DisplayManagerGlobal instance;
    public static DisplayInfo info;

    public static void reset() {
        instance = new DisplayManagerGlobal();
        info = new DisplayInfo(1920, 1080, new DisplayAddress.Physical(42L));
    }

    public static DisplayManagerGlobal getInstance() { return instance; }

    public DisplayInfo getDisplayInfo(int displayId) {
        return displayId == 0 ? info : null;
    }
}
