// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

/** One captured RGB frame. The pixel array is handed over, not copied; callers must not modify it. */
final class Frame {
    final byte[] rgb;
    final int width;
    final int height;

    Frame(byte[] rgb, int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height * 3 != rgb.length) {
            throw new IllegalArgumentException("RGB length " + rgb.length + " does not match " + width + "x" + height);
        }
        this.rgb = rgb;
        this.width = width;
        this.height = height;
    }

    String size() {
        return width + "x" + height;
    }
}
