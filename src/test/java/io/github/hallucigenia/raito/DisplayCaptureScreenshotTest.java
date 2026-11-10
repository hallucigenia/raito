// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import android.graphics.Bitmap;
import android.view.SurfaceControl;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public final class DisplayCaptureScreenshotTest {
    private static DisplayCaptureScreenshot open(String owner) throws Exception {
        return new DisplayCaptureScreenshot(owner, BuiltInDisplay.query(), new Bitmaps());
    }

    public void captures_the_built_in_display_at_the_requested_size() throws Exception {
        Stubs.reset();
        DisplayCaptureScreenshot api = open(DisplayCaptureScreenshot.SURFACE_CONTROL);
        Frame frame = api.capture(160, 90);
        Check.equal("160x90", frame.size(), "frame size");
        Check.equal("160x90 token=built-in", SurfaceControl.lastRequest, "capture arguments");
        Check.equal("SurfaceControl.captureDisplay", api.name(), "name");
    }

    public void recycles_the_bitmap_before_closing_the_hardware_buffer() throws Exception {
        Stubs.reset();
        open(DisplayCaptureScreenshot.SURFACE_CONTROL).capture(160, 90);
        Check.equal(Arrays.asList("copy screen", "recycle screen-copy", "recycle screen", "close buffer"),
            Stubs.EVENTS, "cleanup order");
    }

    public void closes_the_hardware_buffer_when_reading_fails() throws Exception {
        Stubs.reset();
        Bitmap.failGetPixels = true;
        final DisplayCaptureScreenshot api = open(DisplayCaptureScreenshot.SURFACE_CONTROL);
        Check.fails(InvocationTargetException.class, () -> api.capture(160, 90), "read fails");
        Check.equal("close buffer", Stubs.EVENTS.get(Stubs.EVENTS.size() - 1), "buffer closed last");
    }

    public void null_result_is_a_transient_error() throws Exception {
        Stubs.reset();
        SurfaceControl.captureReturnsNull = true;
        final DisplayCaptureScreenshot api = open(DisplayCaptureScreenshot.SURFACE_CONTROL);
        IOException error = Check.fails(IOException.class, () -> api.capture(160, 90), "null");
        Check.isTrue(error.getMessage().contains("SurfaceControl.captureDisplay"), "names the API");
        Check.isTrue(!Captures.isUnsupported(error), "retried, not unsupported");
    }

    public void builds_capture_args_once_per_size() throws Exception {
        Stubs.reset();
        DisplayCaptureScreenshot api = open(DisplayCaptureScreenshot.SURFACE_CONTROL);
        api.capture(160, 90);
        api.capture(160, 90);
        Check.equal(1, SurfaceControl.buildersCreated, "reused for the same size");
        api.capture(80, 45);
        Check.equal(2, SurfaceControl.buildersCreated, "rebuilt for a new size");
        Check.equal("80x45 token=built-in", SurfaceControl.lastRequest, "new size used");
    }

    public void api_absent_from_the_firmware_is_unsupported() {
        Stubs.reset();
        // The stubs model Android 12–13, where android.window.ScreenCapture does not exist.
        ClassNotFoundException error = Check.fails(ClassNotFoundException.class,
            () -> open(DisplayCaptureScreenshot.SCREEN_CAPTURE), "Android 14 API missing");
        Check.isTrue(Captures.isUnsupported(error), "moves on to the next API");
    }

    public void missing_display_token_is_a_transient_error() {
        Stubs.reset();
        SurfaceControl.internalToken = null;
        IOException error = Check.fails(IOException.class,
            () -> open(DisplayCaptureScreenshot.SURFACE_CONTROL), "no token");
        Check.isTrue(!Captures.isUnsupported(error), "retried, not unsupported");
    }
}
