// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import android.view.SurfaceControl;
import java.io.IOException;

public final class LegacyScreenshotTest {
    public void captures_the_whole_display_at_the_requested_size() throws Exception {
        Stubs.reset();
        LegacyScreenshot api = new LegacyScreenshot(new Bitmaps());
        Frame frame = api.capture(160, 90);
        Check.equal("160x90", frame.size(), "frame size");
        Check.equal("160x90 crop=full rotation=0", SurfaceControl.lastRequest, "screenshot arguments");
        Check.equal("SurfaceControl.screenshot", api.name(), "name");
    }

    public void null_screenshot_is_a_transient_error() throws Exception {
        Stubs.reset();
        SurfaceControl.screenshotReturnsNull = true;
        final LegacyScreenshot api = new LegacyScreenshot(new Bitmaps());
        IOException error = Check.fails(IOException.class, () -> api.capture(160, 90), "null");
        Check.isTrue(!Captures.isUnsupported(error), "retried, not unsupported");
    }
}
