// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.IOException;

/** Size and physical id of the TV's built-in display (Android display 0), via {@code DisplayManagerGlobal}. */
final class BuiltInDisplay {
    static final int DEFAULT_DISPLAY = 0;

    final int width;
    final int height;
    /** SurfaceFlinger's physical display id, or null before Android 10 or when not reported. */
    final Long physicalId;

    private BuiltInDisplay(int width, int height, Long physicalId) {
        this.width = width;
        this.height = height;
        this.physicalId = physicalId;
    }

    static BuiltInDisplay query() throws Exception {
        Object global = Class.forName("android.hardware.display.DisplayManagerGlobal")
            .getMethod("getInstance").invoke(null);
        // Null until system_server publishes the display service, for example early in boot.
        if (global == null) throw new IOException("Display service is not available yet");
        Object info = global.getClass().getMethod("getDisplayInfo", int.class).invoke(global, DEFAULT_DISPLAY);
        if (info == null) throw new IOException("Built-in display is not available");
        int width = info.getClass().getField("logicalWidth").getInt(info);
        int height = info.getClass().getField("logicalHeight").getInt(info);
        if (width <= 0 || height <= 0) throw new IOException("Invalid display geometry " + width + "x" + height);
        return new BuiltInDisplay(width, height, physicalId(info));
    }

    private static Long physicalId(Object info) {
        try {
            Object address = info.getClass().getField("address").get(info);
            if (address == null) return null;
            return (Long) address.getClass().getMethod("getPhysicalDisplayId").invoke(address);
        } catch (ReflectiveOperationException e) {
            // No DisplayAddress before Android 10, or not a physical address. Callers fall back to
            // SurfaceFlinger's first physical display.
            return null;
        }
    }

    String size() {
        return width + "x" + height;
    }
}
