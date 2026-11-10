// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.lang.reflect.Method;

/**
 * Android 9–11: {@code SurfaceControl.screenshot(Rect, int, int, int)} captures the built-in display
 * into a hardware bitmap. An empty crop means the whole display. Removed in later releases.
 */
final class LegacyScreenshot implements ScreenshotApi {
    static final String NAME = "SurfaceControl.screenshot";
    private static final int ROTATION_0 = 0;

    private final Object emptyCrop;
    private final Method screenshot;
    private final Bitmaps bitmaps;

    LegacyScreenshot(Bitmaps bitmaps) throws ReflectiveOperationException {
        this.bitmaps = bitmaps;
        Class<?> rect = Class.forName("android.graphics.Rect");
        screenshot = Class.forName("android.view.SurfaceControl")
            .getMethod("screenshot", rect, int.class, int.class, int.class);
        // Never modified: SurfaceFlinger only reads the crop.
        emptyCrop = rect.getConstructor().newInstance();
    }

    public Frame capture(int width, int height) throws Exception {
        return bitmaps.read(screenshot.invoke(null, emptyCrop, width, height, ROTATION_0), width, height);
    }

    public String name() {
        return NAME;
    }
}
