// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SurfaceCaptureTest {
    public void picks_the_first_api_present_on_the_firmware() throws Exception {
        List<String> opened = new ArrayList<String>();
        ScreenshotApi chosen = SurfaceCapture.select(Arrays.asList(
            missing(opened, "SurfaceControl.screenshot"),
            present(opened, "SurfaceControl.captureDisplay"),
            present(opened, "ScreenCapture.captureDisplay")));
        Check.equal("SurfaceControl.captureDisplay", chosen.name(), "chosen API");
        Check.equal(Arrays.asList("SurfaceControl.screenshot", "SurfaceControl.captureDisplay"), opened,
            "stops at the first present API");
    }

    public void reports_unsupported_with_every_missing_api() {
        UnsupportedOperationException error = Check.fails(UnsupportedOperationException.class,
            () -> SurfaceCapture.select(Arrays.asList(
                missing(new ArrayList<String>(), "SurfaceControl.screenshot"),
                missing(new ArrayList<String>(), "ScreenCapture.captureDisplay"))), "nothing present");
        Check.isTrue(error.getMessage().contains("SurfaceControl.screenshot"), "names the first API");
        Check.isTrue(error.getMessage().contains("ScreenCapture.captureDisplay"), "names the last API");
        Check.isTrue(Captures.isUnsupported(error), "start-up treats it as unsupported");
    }

    public void does_not_mask_a_failure_of_an_api_that_exists() {
        final ScreenshotApi.Factory broken = new ScreenshotApi.Factory() {
            public ScreenshotApi open() throws Exception {
                throw new InvocationTargetException(new IOException("display token unavailable"));
            }
        };
        Check.fails(InvocationTargetException.class,
            () -> SurfaceCapture.select(Arrays.asList(broken, present(new ArrayList<String>(), "later"))),
            "transient failure propagates");
    }

    public void moves_past_an_api_that_reports_itself_unsupported() throws Exception {
        final ScreenshotApi.Factory unloadable = new ScreenshotApi.Factory() {
            public ScreenshotApi open() {
                throw new UnsupportedOperationException("cannot load DisplayControl");
            }
        };
        ScreenshotApi chosen = SurfaceCapture.select(Arrays.asList(unloadable, present(new ArrayList<String>(), "next")));
        Check.equal("next", chosen.name(), "next candidate");
    }

    public void moves_past_an_api_whose_classes_cannot_load() throws Exception {
        final ScreenshotApi.Factory trimmed = new ScreenshotApi.Factory() {
            public ScreenshotApi open() {
                throw new NoClassDefFoundError("android/window/ScreenCapture$DisplayCaptureArgs");
            }
        };
        ScreenshotApi chosen = SurfaceCapture.select(Arrays.asList(trimmed, present(new ArrayList<String>(), "next")));
        Check.equal("next", chosen.name(), "next candidate");
    }

    public void opens_the_oldest_api_the_firmware_still_has() throws Exception {
        Stubs.reset();
        // The stubs have the Android 9–11 screenshot method, so it wins over captureDisplay.
        SurfaceCapture capture = new SurfaceCapture();
        Check.equal("surface api=SurfaceControl.screenshot display=1920x1080", capture.describe(), "chosen API");
        Check.equal("160x90", capture.frame(160).size(), "scaled to the target width");
    }

    public void requires_at_least_one_candidate() {
        Check.fails(UnsupportedOperationException.class,
            () -> SurfaceCapture.select(Collections.<ScreenshotApi.Factory>emptyList()), "empty");
    }

    private static ScreenshotApi.Factory missing(final List<String> opened, final String name) {
        return new ScreenshotApi.Factory() {
            public ScreenshotApi open() throws Exception {
                opened.add(name);
                throw new NoSuchMethodException(name);
            }
        };
    }

    private static ScreenshotApi.Factory present(final List<String> opened, final String name) {
        return new ScreenshotApi.Factory() {
            public ScreenshotApi open() {
                opened.add(name);
                return new ScreenshotApi() {
                    public Frame capture(int width, int height) { return new Frame(new byte[3], 1, 1); }
                    public String name() { return name; }
                };
            }
        };
    }
}
