// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

/** Pixel format conversion into the packed RGB that Hyperion expects. */
final class Pixels {
    private Pixels() { }

    /** Nearest-neighbour downscale of an RGBA buffer (screencap output) to packed RGB. */
    static byte[] sample(byte[] rgba, int sourceWidth, int sourceHeight, int width, int height) {
        if ((long) sourceWidth * sourceHeight * 4 != rgba.length || width <= 0 || height <= 0
                || width > sourceWidth || height > sourceHeight) {
            throw new IllegalArgumentException("Invalid sampling geometry");
        }
        byte[] rgb = new byte[width * height * 3];
        int dst = 0;
        for (int y = 0; y < height; y++) {
            int row = (int) ((long) y * sourceHeight / height) * sourceWidth;
            for (int x = 0; x < width; x++) {
                int p = (row + (int) ((long) x * sourceWidth / width)) * 4;
                rgb[dst++] = rgba[p];
                rgb[dst++] = rgba[p + 1];
                rgb[dst++] = rgba[p + 2];
            }
        }
        return rgb;
    }

    /** Android {@code Bitmap.getPixels} ARGB ints to packed RGB. */
    static byte[] rgb(int[] argb) {
        byte[] rgb = new byte[argb.length * 3];
        int dst = 0;
        for (int c : argb) {
            rgb[dst++] = (byte) (c >>> 16);
            rgb[dst++] = (byte) (c >>> 8);
            rgb[dst++] = (byte) c;
        }
        return rgb;
    }
}
