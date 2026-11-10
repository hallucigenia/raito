// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import android.hardware.display.DisplayManagerGlobal;
import android.view.DisplayAddress;
import android.view.DisplayInfo;
import java.io.IOException;

public final class BuiltInDisplayTest {
    public void reads_logical_size_and_physical_id() throws Exception {
        Stubs.reset();
        BuiltInDisplay display = BuiltInDisplay.query();
        Check.equal("1920x1080", display.size(), "size");
        Check.equal(42L, display.physicalId, "physical id");
    }

    public void physical_id_is_absent_without_a_physical_address() throws Exception {
        Stubs.reset();
        DisplayManagerGlobal.info = new DisplayInfo(1920, 1080, null);
        Check.equal(null, BuiltInDisplay.query().physicalId, "no address");
        DisplayManagerGlobal.info = new DisplayInfo(1920, 1080, new DisplayAddress.Network());
        Check.equal(null, BuiltInDisplay.query().physicalId, "network address");
    }

    public void missing_display_service_is_a_transient_error() {
        Stubs.reset();
        DisplayManagerGlobal.instance = null;
        IOException error = Check.fails(IOException.class, BuiltInDisplay::query, "service not published yet");
        Check.isTrue(error.getMessage().contains("Display service"), "explains the cause");
    }

    public void missing_display_is_a_transient_error() {
        Stubs.reset();
        DisplayManagerGlobal.info = null;
        Check.fails(IOException.class, BuiltInDisplay::query, "no display 0");
    }

    public void rejects_invalid_geometry() {
        Stubs.reset();
        DisplayManagerGlobal.info = new DisplayInfo(0, 1080, null);
        Check.fails(IOException.class, BuiltInDisplay::query, "zero width");
    }
}
