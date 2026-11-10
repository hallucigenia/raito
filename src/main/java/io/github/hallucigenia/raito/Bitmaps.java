// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.IOException;
import java.lang.reflect.Method;

/** Reads an {@code android.graphics.Bitmap} screenshot into an RGB frame, by reflection. */
final class Bitmaps {
    private final Method copy;
    private final Method getWidth;
    private final Method getHeight;
    private final Method getPixels;
    private final Method recycle;
    private final Object argb8888;
    private int[] pixels;

    Bitmaps() throws ReflectiveOperationException {
        Class<?> bitmap = Class.forName("android.graphics.Bitmap");
        Class<?> config = Class.forName("android.graphics.Bitmap$Config");
        copy = bitmap.getMethod("copy", config, boolean.class);
        getWidth = bitmap.getMethod("getWidth");
        getHeight = bitmap.getMethod("getHeight");
        getPixels = bitmap.getMethod("getPixels", int[].class, int.class, int.class,
            int.class, int.class, int.class, int.class);
        recycle = bitmap.getMethod("recycle");
        argb8888 = config.getField("ARGB_8888").get(null);
    }

    /**
     * Copies a screenshot (a hardware bitmap, whose pixels cannot be read directly) to software
     * memory and converts it to RGB. The screenshot must have the requested size. Recycles both
     * bitmaps, even on failure; a failure to recycle never hides the original error. Not
     * thread-safe: reuses one pixel buffer.
     */
    Frame read(Object screenshot, int width, int height) throws Exception {
        if (screenshot == null) throw new IOException("Physical display screenshot returned null");
        try (Recycled original = new Recycled(screenshot)) {
            requireSize(original.bitmap, width, height);
            Object copied = copy.invoke(original.bitmap, argb8888, false);
            if (copied == null) throw new IOException("Could not read screenshot bitmap");
            try (Recycled readable = new Recycled(copied)) {
                return rgb(readable.bitmap, width, height);
            }
        }
    }

    /** Firmware that ignored the requested size would otherwise send full-resolution frames. */
    private void requireSize(Object bitmap, int width, int height) throws Exception {
        int actualWidth = (Integer) getWidth.invoke(bitmap);
        int actualHeight = (Integer) getHeight.invoke(bitmap);
        if (actualWidth != width || actualHeight != height) {
            throw new IOException("Screenshot is " + actualWidth + "x" + actualHeight
                + ", requested " + width + "x" + height);
        }
    }

    private Frame rgb(Object bitmap, int width, int height) throws Exception {
        if (pixels == null || pixels.length != width * height) pixels = new int[width * height];
        getPixels.invoke(bitmap, pixels, 0, width, 0, 0, width, height);
        return new Frame(Pixels.rgb(pixels), width, height);
    }

    /** Recycles a bitmap when closed, so try-with-resources keeps the first error. */
    private final class Recycled implements AutoCloseable {
        final Object bitmap;

        Recycled(Object bitmap) {
            this.bitmap = bitmap;
        }

        public void close() throws ReflectiveOperationException {
            recycle.invoke(bitmap);
        }
    }
}
