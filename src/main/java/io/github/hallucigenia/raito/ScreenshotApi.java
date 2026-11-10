// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

/**
 * One generation of the hidden physical-display screenshot API. Each is resolved by reflection;
 * opening one whose classes or methods are absent fails with a {@link ReflectiveOperationException}
 * or {@link UnsupportedOperationException} (see {@link Captures#isUnsupported}).
 */
interface ScreenshotApi {
    /** Screenshot of the built-in display, scaled by SurfaceFlinger to about width×height. */
    Frame capture(int width, int height) throws Exception;

    /** The Android API in use, for logs and probe reports. */
    String name();

    interface Factory {
        ScreenshotApi open() throws Exception;
    }
}
