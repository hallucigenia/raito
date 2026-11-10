// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import android.graphics.Bitmap;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public final class BitmapsTest {
    private static Bitmap screen() {
        return new Bitmap("screen", 2, 1, 0xff102030, Bitmap.Config.HARDWARE);
    }

    public void reads_rgb_from_a_software_copy_and_recycles_both() throws Exception {
        Stubs.reset();
        Frame frame = new Bitmaps().read(screen(), 2, 1);
        Check.equal("2x1", frame.size(), "frame size");
        Check.bytes(new byte[] {0x10, 0x20, 0x30, 0x10, 0x20, 0x30}, frame.rgb, "RGB");
        Check.equal(Arrays.asList("copy screen", "recycle screen-copy", "recycle screen"), Stubs.EVENTS,
            "copy first, then recycle copy and original");
    }

    public void recycles_both_and_keeps_the_original_error_when_cleanup_also_fails() throws Exception {
        Stubs.reset();
        Bitmap.failGetPixels = true;
        Bitmap.failRecycle = true;
        final Bitmaps bitmaps = new Bitmaps();
        InvocationTargetException error = Check.fails(InvocationTargetException.class,
            () -> bitmaps.read(screen(), 2, 1), "read fails");
        Check.equal("getPixels failed", error.getCause().getMessage(), "original error wins");
        Check.isTrue(Stubs.EVENTS.contains("recycle screen-copy"), "copy recycled");
        Check.isTrue(Stubs.EVENTS.contains("recycle screen"), "original recycled despite the first recycle failing");
    }

    public void rejects_a_null_screenshot() throws Exception {
        Stubs.reset();
        final Bitmaps bitmaps = new Bitmaps();
        Check.fails(IOException.class, () -> bitmaps.read(null, 2, 1), "null screenshot");
    }

    public void recycles_the_screenshot_when_the_copy_fails() throws Exception {
        Stubs.reset();
        Bitmap.copyReturnsNull = true;
        final Bitmaps bitmaps = new Bitmaps();
        Check.fails(IOException.class, () -> bitmaps.read(screen(), 2, 1), "no copy");
        Check.equal(Arrays.asList("copy screen", "recycle screen"), Stubs.EVENTS, "original recycled");
    }

    public void rejects_a_screenshot_that_ignores_the_requested_size() throws Exception {
        Stubs.reset();
        final Bitmaps bitmaps = new Bitmaps();
        IOException error = Check.fails(IOException.class, () -> bitmaps.read(screen(), 160, 90), "wrong size");
        Check.isTrue(error.getMessage().contains("2x1") && error.getMessage().contains("160x90"), "names both sizes");
        Check.equal(Arrays.asList("recycle screen"), Stubs.EVENTS, "recycled without copying");
    }
}
