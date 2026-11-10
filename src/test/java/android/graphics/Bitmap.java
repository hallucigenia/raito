// SPDX-License-Identifier: Apache-2.0
package android.graphics;

import io.github.hallucigenia.raito.Stubs;
import java.util.Arrays;

/** Test stub. Like the real class, a HARDWARE bitmap's pixels cannot be read until it is copied. */
public final class Bitmap {
    public enum Config { ARGB_8888, HARDWARE }

    public static boolean copyReturnsNull;
    public static boolean failGetPixels;
    public static boolean failRecycle;

    private final String name;
    private final int width;
    private final int height;
    private final int color;
    private final Config config;

    public Bitmap(String name, int width, int height, int color, Config config) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.color = color;
        this.config = config;
    }

    public static void reset() {
        copyReturnsNull = false;
        failGetPixels = false;
        failRecycle = false;
    }

    public Bitmap copy(Config target, boolean mutable) {
        Stubs.EVENTS.add("copy " + name);
        return copyReturnsNull ? null : new Bitmap(name + "-copy", width, height, color, target);
    }

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public void getPixels(int[] pixels, int offset, int stride, int x, int y, int w, int h) {
        if (config == Config.HARDWARE) throw new IllegalStateException("unable to getPixels(), pixel access is not supported on Config#HARDWARE bitmaps");
        if (failGetPixels) throw new IllegalStateException("getPixels failed");
        Arrays.fill(pixels, offset, offset + w * h, color);
    }

    public void recycle() {
        Stubs.EVENTS.add("recycle " + name);
        if (failRecycle) throw new IllegalStateException("recycle failed");
    }
}
