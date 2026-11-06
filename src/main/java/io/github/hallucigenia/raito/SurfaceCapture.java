// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

/**
 * Physical-display screenshot through the Android 10/11 {@code SurfaceControl} API, scaled by
 * SurfaceFlinger and copied into a software bitmap. It needs no virtual display and no
 * MediaProjection, and excludes secure layers. Android 12 and later changed these hidden
 * methods; there this backend reports itself unsupported (see {@code probe}).
 */
final class SurfaceCapture implements Capture {
    private final Class<?> rect;
    private final Method screenshot;
    private final Method copy;
    private final Method getPixels;
    private final Method recycle;
    private final Object argb8888;
    private final int displayWidth;
    private final int displayHeight;
    private int[] pixels;

    SurfaceCapture() throws Exception {
        Class<?> surfaceControl = Class.forName("android.view.SurfaceControl");
        Class<?> bitmap = Class.forName("android.graphics.Bitmap");
        Class<?> config = Class.forName("android.graphics.Bitmap$Config");
        Class<?> binder = Class.forName("android.os.IBinder");
        rect = Class.forName("android.graphics.Rect");
        Object display;
        Object configs;
        int active;
        try {
            screenshot = surfaceControl.getDeclaredMethod("screenshot", rect, int.class, int.class, int.class);
            display = surfaceControl.getMethod("getInternalDisplayToken").invoke(null);
            if (display == null) throw new IOException("Physical display is disconnected");
            active = (Integer) surfaceControl.getMethod("getActiveConfig", binder).invoke(null, display);
            configs = surfaceControl.getMethod("getDisplayConfigs", binder).invoke(null, display);
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException("surface backend needs the Android 10/11 SurfaceControl API; "
                + "this device reports API " + DeviceInfo.sdkInt() + " (missing " + e.getMessage() + ")", e);
        }
        copy = bitmap.getMethod("copy", config, boolean.class);
        getPixels = bitmap.getMethod("getPixels", int[].class, int.class, int.class,
            int.class, int.class, int.class, int.class);
        recycle = bitmap.getMethod("recycle");
        argb8888 = config.getField("ARGB_8888").get(null);
        Object mode = Array.get(configs, active);
        displayWidth = mode.getClass().getField("width").getInt(mode);
        displayHeight = mode.getClass().getField("height").getInt(mode);
        if (displayWidth <= 0 || displayHeight <= 0) throw new IOException("Invalid display geometry");
    }

    public Frame frame(int targetWidth) throws Exception {
        int width = Math.min(targetWidth, displayWidth);
        int height = Math.max(1, (int) ((long) displayHeight * width / displayWidth));
        Object original = screenshot.invoke(null, rect.getConstructor().newInstance(), width, height, 0);
        if (original == null) throw new IOException("Physical display screenshot returned null");
        Object readable = null;
        try {
            // The screenshot is a hardware bitmap; copy it to software memory before reading pixels.
            readable = copy.invoke(original, argb8888, false);
            if (readable == null) throw new IOException("Could not read screenshot bitmap");
            if (pixels == null || pixels.length != width * height) pixels = new int[width * height];
            getPixels.invoke(readable, pixels, 0, width, 0, 0, width, height);
            return new Frame(Pixels.rgb(pixels), width, height);
        } finally {
            if (readable != null) recycle.invoke(readable);
            recycle.invoke(original);
        }
    }

    public String describe() {
        return "surface display=" + displayWidth + "x" + displayHeight;
    }

    public void close() {
        // Holds no native resources between frames.
    }
}
