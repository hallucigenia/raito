// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

public final class SizeTest {
    public void scales_full_hd_to_target_width_keeping_aspect() {
        Size size = Size.fit(1920, 1080, 160);
        Check.equal(160, size.width, "width");
        Check.equal(90, size.height, "height");
    }

    public void scales_4k_to_the_same_frame_as_full_hd() {
        Size size = Size.fit(3840, 2160, 160);
        Check.equal("160x90", size.width + "x" + size.height, "4K source");
    }

    public void never_upscales_a_small_source() {
        Size size = Size.fit(100, 50, 160);
        Check.equal("100x50", size.width + "x" + size.height, "small source");
    }

    public void keeps_at_least_one_row_for_extreme_aspect() {
        Check.equal(1, Size.fit(10000, 1, 160).height, "very wide source");
    }

    public void output_always_fits_inside_the_source_for_sampling() {
        int[][] sources = {{1920, 1080}, {1366, 768}, {1280, 720}, {720, 576}, {160, 90}, {161, 1}, {1, 1}};
        for (int[] source : sources) {
            for (int target : new int[] {1, 64, 159, 160, 161, 640}) {
                Size size = Size.fit(source[0], source[1], target);
                Check.isTrue(size.width >= 1 && size.width <= source[0], "width within source " + source[0]);
                Check.isTrue(size.height >= 1 && size.height <= source[1], "height within source " + source[1]);
                // Must satisfy Pixels.sample's preconditions without throwing.
                Pixels.sample(new byte[source[0] * source[1] * 4], source[0], source[1], size.width, size.height);
            }
        }
    }

    public void rejects_non_positive_dimensions() {
        Check.fails(IllegalArgumentException.class, () -> Size.fit(0, 1080, 160), "zero source width");
        Check.fails(IllegalArgumentException.class, () -> Size.fit(1920, -1, 160), "negative source height");
        Check.fails(IllegalArgumentException.class, () -> Size.fit(1920, 1080, 0), "zero target width");
    }
}
