// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

public final class PixelsTest {
    static final byte F = (byte) 255;

    public void converts_rgba_to_rgb() {
        byte[] rgba = {F, 0, 0, F, 0, F, 0, F, 0, 0, F, F, F, F, F, F};
        Check.bytes(new byte[] {F, 0, 0, 0, F, 0, 0, 0, F, F, F, F},
            Pixels.sample(rgba, 2, 2, 2, 2), "RGBA channel conversion");
    }

    public void samples_nearest_pixel_when_downscaling() {
        byte[] rgba = new byte[4 * 4 * 4];
        for (int i = 0; i < 16; i++) rgba[i * 4] = (byte) i;
        byte[] rgb = Pixels.sample(rgba, 4, 4, 2, 2);
        Check.bytes(new byte[] {0, 0, 0, 2, 0, 0, 8, 0, 0, 10, 0, 0}, rgb, "every other pixel");
    }

    public void rejects_inconsistent_geometry() {
        Check.fails(IllegalArgumentException.class,
            () -> Pixels.sample(new byte[8], 2, 2, 2, 2), "buffer too short");
        Check.fails(IllegalArgumentException.class,
            () -> Pixels.sample(new byte[16], 2, 2, 4, 4), "upscaling");
    }

    public void converts_android_argb_ints_to_rgb() {
        Check.bytes(new byte[] {F, 0, 0, 0, F, 0, 0, 0, F, F, F, F},
            Pixels.rgb(new int[] {0xffff0000, 0xff00ff00, 0xff0000ff, 0xffffffff}), "ARGB to RGB");
    }
}
