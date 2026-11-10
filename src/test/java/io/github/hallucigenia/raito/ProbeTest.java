// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import android.view.SurfaceControl;

public final class ProbeTest {
    public void reports_the_chosen_api_even_when_capture_fails() throws Exception {
        Stubs.reset();
        SurfaceControl.screenshotReturnsNull = true;
        String report = Probe.run(new String[] {"--backend", "surface", "--frames", "1"});
        Check.isTrue(report.contains("\"ok\":false"), "failed: " + report);
        Check.isTrue(report.contains("\"source\":\"surface api=SurfaceControl.screenshot display=1920x1080\""),
            "names the API that failed: " + report);
    }

    public void reports_the_chosen_api_when_capture_works() throws Exception {
        Stubs.reset();
        String report = Probe.run(new String[] {"--backend", "surface", "--frames", "1"});
        Check.isTrue(report.contains("\"ok\":true"), "works: " + report);
        Check.isTrue(report.contains("\"source\":\"surface api=SurfaceControl.screenshot"), "names the API: " + report);
    }
}
