// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public final class CapturesTest {
    public void missing_hidden_api_is_unsupported() {
        Check.isTrue(Captures.isUnsupported(new ClassNotFoundException("android.view.SurfaceControl")), "missing class");
        Check.isTrue(Captures.isUnsupported(new NoSuchMethodException("screenshot")), "missing method");
        Check.isTrue(Captures.isUnsupported(new UnsupportedOperationException("API 33")), "explicit");
    }

    public void failing_call_or_io_is_transient() {
        Check.isTrue(!Captures.isUnsupported(new InvocationTargetException(new IllegalStateException())),
            "an existing API threw");
        Check.isTrue(!Captures.isUnsupported(new IOException("Physical display is disconnected")), "io error");
    }

    public void rejects_unknown_backend() {
        Check.fails(IllegalArgumentException.class, () -> Captures.forBackend("mediaprojection"), "unknown");
    }
}
