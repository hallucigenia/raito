// SPDX-License-Identifier: Apache-2.0
package android.view;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.IBinder;

/**
 * Test stub with both the Android 9–11 {@code screenshot} and the Android 12–13 {@code captureDisplay}
 * shapes. Real firmware has one or the other. Screenshots are filled with {@link #COLOR}.
 */
public final class SurfaceControl {
    public static final int COLOR = 0xff102030;
    public static final IBinder BUILT_IN = new IBinder() { };

    public static IBinder internalToken;
    /** Thrown by getInternalDisplayToken when set: a RuntimeException or an Error. */
    public static Throwable internalTokenFailure;
    public static boolean screenshotReturnsNull;
    public static boolean captureReturnsNull;
    public static int buildersCreated;
    public static String lastRequest;

    private SurfaceControl() { }

    public static void reset() {
        internalToken = BUILT_IN;
        internalTokenFailure = null;
        screenshotReturnsNull = false;
        captureReturnsNull = false;
        buildersCreated = 0;
        lastRequest = null;
    }

    public static Bitmap screenshot(Rect sourceCrop, int width, int height, int rotation) {
        lastRequest = width + "x" + height + " crop=" + (sourceCrop.isEmpty() ? "full" : "partial") + " rotation=" + rotation;
        return screenshotReturnsNull ? null : new Bitmap("screen", width, height, COLOR, Bitmap.Config.HARDWARE);
    }

    public static IBinder getInternalDisplayToken() {
        if (internalTokenFailure instanceof Error) throw (Error) internalTokenFailure;
        if (internalTokenFailure instanceof RuntimeException) throw (RuntimeException) internalTokenFailure;
        return internalToken;
    }

    public static ScreenshotHardwareBuffer captureDisplay(DisplayCaptureArgs args) {
        lastRequest = args.width + "x" + args.height + " token=" + (args.token == BUILT_IN ? "built-in" : "other");
        if (captureReturnsNull) return null;
        return new ScreenshotHardwareBuffer(new Bitmap("screen", args.width, args.height, COLOR, Bitmap.Config.HARDWARE));
    }

    public static class DisplayCaptureArgs {
        final IBinder token;
        final int width;
        final int height;

        DisplayCaptureArgs(IBinder token, int width, int height) {
            this.token = token;
            this.width = width;
            this.height = height;
        }

        public static class Builder {
            private final IBinder token;
            private int width;
            private int height;

            public Builder(IBinder displayToken) {
                buildersCreated++;
                token = displayToken;
            }

            public Builder setSize(int w, int h) {
                width = w;
                height = h;
                return this;
            }

            public DisplayCaptureArgs build() {
                return new DisplayCaptureArgs(token, width, height);
            }
        }
    }

    public static class ScreenshotHardwareBuffer {
        private final Bitmap bitmap;
        private final HardwareBuffer buffer = new HardwareBuffer();

        ScreenshotHardwareBuffer(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        public Bitmap asBitmap() { return bitmap; }

        public HardwareBuffer getHardwareBuffer() { return buffer; }
    }
}
