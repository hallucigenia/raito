// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import android.view.SurfaceControl;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public final class DisplayTokenTest {
    public void prefers_the_physical_id_of_the_default_display() throws Exception {
        Check.equal(42L, DisplayToken.physicalId(42L, new long[] {7L, 42L}), "default display id");
    }

    public void falls_back_to_the_first_physical_display() throws Exception {
        Check.equal(7L, DisplayToken.physicalId(null, new long[] {7L, 42L}), "no address");
        Check.equal(7L, DisplayToken.physicalId(99L, new long[] {7L, 42L}), "stale address");
    }

    public void fails_when_no_physical_display_exists() {
        Check.fails(IOException.class, () -> DisplayToken.physicalId(null, new long[0]), "no displays");
        Check.fails(IOException.class, () -> DisplayToken.physicalId(null, null), "null list");
    }

    public void uses_the_internal_display_token_where_surface_control_has_it() throws Exception {
        Stubs.reset();
        Check.isTrue(DisplayToken.builtIn(BuiltInDisplay.query()) == SurfaceControl.BUILT_IN,
            "Android 10–13 token");
    }

    public void falls_back_to_display_control_when_the_internal_token_has_no_native() throws Exception {
        Stubs.reset();
        SurfaceControl.internalTokenFailure = new UnsatisfiedLinkError("nativeGetInternalDisplayToken");
        final BuiltInDisplay display = BuiltInDisplay.query();
        // A plain JVM has no system server classpath, so reaching the DisplayControl loader fails.
        UnsupportedOperationException error = Check.fails(UnsupportedOperationException.class,
            () -> DisplayToken.builtIn(display), "fallback attempted");
        Check.isTrue(error.getMessage().contains("DisplayControl"), "tried DisplayControl");
    }

    public void propagates_a_failure_of_the_internal_token_call() throws Exception {
        Stubs.reset();
        SurfaceControl.internalTokenFailure = new IllegalStateException("SurfaceFlinger died");
        final BuiltInDisplay display = BuiltInDisplay.query();
        Check.fails(InvocationTargetException.class, () -> DisplayToken.builtIn(display), "real failure");
    }

    public void uses_the_system_server_classpath_from_the_environment() {
        String path = "/system/framework/com.android.location.provider.jar:/system/framework/services.jar";
        Check.equal(path, DisplayToken.serverClasspath(path), "from environment");
    }

    public void defaults_to_services_jar_without_environment() {
        Check.equal("/system/framework/services.jar", DisplayToken.serverClasspath(null), "unset");
        Check.equal("/system/framework/services.jar", DisplayToken.serverClasspath("  "), "blank");
    }
}
