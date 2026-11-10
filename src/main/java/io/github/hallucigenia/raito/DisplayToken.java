// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * The SurfaceFlinger token of the built-in physical display, which the Android 12+ capture APIs
 * take as their source. Android 10–13 expose it on {@code SurfaceControl}; Android 14 moved the
 * physical display methods to {@code com.android.server.display.DisplayControl}, which lives in the
 * system server classpath with its natives in {@code libandroid_servers}.
 */
final class DisplayToken {
    static final String DEFAULT_SERVER_CLASSPATH = "/system/framework/services.jar";
    private static Class<?> displayControl;

    private DisplayToken() { }

    static Object builtIn(BuiltInDisplay display) throws Exception {
        Object token;
        try {
            token = Class.forName("android.view.SurfaceControl").getMethod("getInternalDisplayToken").invoke(null);
        } catch (ReflectiveOperationException e) {
            // Gone in Android 14, or present without its native: use DisplayControl. A failing call is real.
            if (!Captures.isUnsupported(e)) throw e;
            token = physicalToken(display);
        }
        if (token == null) throw new IOException("Built-in display token is unavailable");
        return token;
    }

    private static Object physicalToken(BuiltInDisplay display) throws Exception {
        Class<?> control = displayControl();
        long[] ids = (long[]) control.getMethod("getPhysicalDisplayIds").invoke(null);
        return control.getMethod("getPhysicalDisplayToken", long.class).invoke(null, physicalId(display.physicalId, ids));
    }

    /** The default display's physical id when SurfaceFlinger lists it, else the first physical display. */
    static long physicalId(Long preferred, long[] ids) throws IOException {
        if (ids == null || ids.length == 0) throw new IOException("No physical display found");
        if (preferred != null) {
            for (long id : ids) if (id == preferred) return id;
        }
        return ids[0];
    }

    /** The {@code SYSTEMSERVERCLASSPATH} that init exports to the shell, or services.jar without it. */
    static String serverClasspath(String environment) {
        return environment == null || environment.trim().isEmpty() ? DEFAULT_SERVER_CLASSPATH : environment;
    }

    /**
     * Loads DisplayControl once per process. Its natives are registered by loading
     * {@code libandroid_servers} into this process. One Android 14 TV crashed this way (SIGSEGV in the
     * VM's shutdown thread) when the process exited, after all output had been written.
     */
    private static synchronized Class<?> displayControl() {
        if (displayControl != null) return displayControl;
        try {
            Method create = Class.forName("com.android.internal.os.ClassLoaderFactory").getDeclaredMethod(
                "createClassLoader", String.class, String.class, String.class, ClassLoader.class,
                int.class, boolean.class, String.class);
            ClassLoader loader = (ClassLoader) create.invoke(null,
                serverClasspath(System.getenv("SYSTEMSERVERCLASSPATH")), null, null,
                ClassLoader.getSystemClassLoader(), 0, true, null);
            Class<?> control = loader.loadClass("com.android.server.display.DisplayControl");
            Method loadLibrary = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
            loadLibrary.setAccessible(true);
            loadLibrary.invoke(Runtime.getRuntime(), control, "android_servers");
            displayControl = control;
            return control;
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new UnsupportedOperationException(
                "cannot load DisplayControl from the system server classpath: " + Log.describe(e), e);
        }
    }
}
