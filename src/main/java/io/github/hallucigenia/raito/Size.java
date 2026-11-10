// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

/** Output frame size: a source scaled down to a target width, keeping its aspect ratio. */
final class Size {
    final int width;
    final int height;

    private Size(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Scales {@code source} to {@code targetWidth} pixels wide; never upscales and keeps at least one row. */
    static Size fit(int sourceWidth, int sourceHeight, int targetWidth) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0) {
            throw new IllegalArgumentException("Invalid size " + sourceWidth + "x" + sourceHeight
                + " for target width " + targetWidth);
        }
        int width = Math.min(targetWidth, sourceWidth);
        int height = Math.max(1, (int) ((long) sourceHeight * width / sourceWidth));
        return new Size(width, height);
    }
}
