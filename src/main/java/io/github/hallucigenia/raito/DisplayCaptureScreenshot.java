// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Android 12+: {@code captureDisplay(DisplayCaptureArgs)} returns a {@code ScreenshotHardwareBuffer}.
 * The API sits on {@code android.view.SurfaceControl} in Android 12–13 and moved, unchanged in
 * shape, to {@code android.window.ScreenCapture} in Android 14. Secure layers are excluded (the
 * builder's default).
 */
final class DisplayCaptureScreenshot implements ScreenshotApi {
    static final String SURFACE_CONTROL = "android.view.SurfaceControl";
    static final String SCREEN_CAPTURE = "android.window.ScreenCapture";

    private final String name;
    private final Constructor<?> newBuilder;
    private final Method setSize;
    private final Method build;
    private final Method captureDisplay;
    private final Method asBitmap;
    private final Method getHardwareBuffer;
    private final Method closeBuffer;
    private final Object displayToken;
    private final Bitmaps bitmaps;
    private Object args;
    private int argsWidth;
    private int argsHeight;

    DisplayCaptureScreenshot(String owner, BuiltInDisplay display, Bitmaps bitmaps) throws Exception {
        this.bitmaps = bitmaps;
        name = owner.substring(owner.lastIndexOf('.') + 1) + ".captureDisplay";
        Class<?> api = Class.forName(owner);
        Class<?> builder = Class.forName(owner + "$DisplayCaptureArgs$Builder");
        Class<?> buffer = Class.forName(owner + "$ScreenshotHardwareBuffer");
        newBuilder = builder.getConstructor(Class.forName("android.os.IBinder"));
        setSize = builder.getMethod("setSize", int.class, int.class);
        build = builder.getMethod("build");
        captureDisplay = api.getMethod("captureDisplay", Class.forName(owner + "$DisplayCaptureArgs"));
        asBitmap = buffer.getMethod("asBitmap");
        getHardwareBuffer = buffer.getMethod("getHardwareBuffer");
        closeBuffer = Class.forName("android.hardware.HardwareBuffer").getMethod("close");
        // Last, once the API is known to exist: on Android 14 this loads system server code.
        displayToken = DisplayToken.builtIn(display);
    }

    public Frame capture(int width, int height) throws Exception {
        Object result = captureDisplay.invoke(null, args(width, height));
        if (result == null) throw new IOException(name + " returned null");
        try (Screenshot screenshot = new Screenshot(result)) {
            return bitmaps.read(asBitmap.invoke(screenshot.result), width, height);
        }
    }

    /** A captureDisplay result. Closing it frees its graphics memory now rather than at garbage collection. */
    private final class Screenshot implements AutoCloseable {
        final Object result;

        Screenshot(Object result) {
            this.result = result;
        }

        public void close() throws ReflectiveOperationException {
            Object buffer = getHardwareBuffer.invoke(result);
            if (buffer != null) closeBuffer.invoke(buffer);
        }
    }

    public String name() {
        return name;
    }

    private Object args(int width, int height) throws Exception {
        if (args == null || width != argsWidth || height != argsHeight) {
            Object builder = newBuilder.newInstance(displayToken);
            setSize.invoke(builder, width, height);
            args = build.invoke(builder);
            argsWidth = width;
            argsHeight = height;
        }
        return args;
    }
}
