// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Physical-display screenshot through SurfaceFlinger's hidden screenshot API, scaled on the GPU and
 * copied into a software bitmap. It needs no virtual display and no MediaProjection, and excludes
 * secure layers. Whether a TV's HDMI video layer is included depends on its SoC and firmware.
 */
final class SurfaceCapture implements Capture {
    private final BuiltInDisplay display;
    private final ScreenshotApi api;

    SurfaceCapture() throws Exception {
        display = BuiltInDisplay.query();
        api = select(candidates(display, new Bitmaps()));
    }

    /**
     * Oldest first: the Android 9–11 API is the one verified on hardware, so it is kept wherever it
     * still exists; the Android 12–13 and 14+ generations follow.
     */
    private static List<ScreenshotApi.Factory> candidates(final BuiltInDisplay display, final Bitmaps bitmaps) {
        return Arrays.asList(
            new ScreenshotApi.Factory() {
                public ScreenshotApi open() throws Exception { return new LegacyScreenshot(bitmaps); }
            },
            new ScreenshotApi.Factory() {
                public ScreenshotApi open() throws Exception {
                    return new DisplayCaptureScreenshot(DisplayCaptureScreenshot.SURFACE_CONTROL, display, bitmaps);
                }
            },
            new ScreenshotApi.Factory() {
                public ScreenshotApi open() throws Exception {
                    return new DisplayCaptureScreenshot(DisplayCaptureScreenshot.SCREEN_CAPTURE, display, bitmaps);
                }
            });
    }

    /**
     * Opens the first candidate present on this firmware. A missing API, or one whose classes or
     * natives cannot load, moves on to the next one; any other failure is real and propagates.
     */
    static ScreenshotApi select(List<ScreenshotApi.Factory> candidates) throws Exception {
        List<String> missing = new ArrayList<String>();
        for (ScreenshotApi.Factory candidate : candidates) {
            try {
                return candidate.open();
            } catch (LinkageError e) {
                missing.add(Log.describe(e));
            } catch (Exception e) {
                if (!Captures.isUnsupported(e)) throw e;
                missing.add(Log.describe(e));
            }
        }
        throw new UnsupportedOperationException("surface backend found no screenshot API on API "
            + DeviceInfo.sdkInt() + "; missing " + missing);
    }

    public Frame frame(int targetWidth) throws Exception {
        // The display size is read once per open. A TV's logical (UI) size normally stays fixed when
        // the HDMI output mode changes, and any capture failure or standby reopens the capture anyway.
        Size size = Size.fit(display.width, display.height, targetWidth);
        return api.capture(size.width, size.height);
    }

    public String describe() {
        return "surface api=" + api.name() + " display=" + display.size();
    }

    public void close() {
        // Holds no native resources between frames.
    }
}
