// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Slow fallback that runs {@code /system/bin/screencap} for every frame. Useful for diagnosis on
 * firmware where the surface backend is unavailable. Assumes the 16-byte raw header
 * (width, height, format, dataspace) used by recent Android releases.
 */
final class ScreencapCapture implements Capture {
    static final int HEADER_BYTES = 16;
    static final long MAX_SOURCE_PIXELS = 16_000_000L;
    static final int TIMEOUT_SECONDS = 3;
    static final int FORMAT_RGBA_8888 = 1;
    static final int FORMAT_RGBX_8888 = 2;

    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactory() {
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "screencap-watchdog");
                thread.setDaemon(true);
                return thread;
            }
        });
    private volatile Process current;
    private byte[] pixels;
    private String lastSource = "screencap";

    public Frame frame(int targetWidth) throws Exception {
        final Process process = new ProcessBuilder("/system/bin/screencap").start();
        current = process;
        ScheduledFuture<?> timeout = watchdog.schedule(new Runnable() {
            public void run() { process.destroy(); }
        }, TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try {
            process.getOutputStream().close();
            InputStream in = process.getInputStream();
            byte[] header = new byte[HEADER_BYTES];
            readFully(in, header);
            ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int sourceWidth = h.getInt();
            int sourceHeight = h.getInt();
            int format = h.getInt();
            if (sourceWidth <= 0 || sourceHeight <= 0 || (long) sourceWidth * sourceHeight > MAX_SOURCE_PIXELS
                    || (format != FORMAT_RGBA_8888 && format != FORMAT_RGBX_8888)) {
                throw new IOException("Unsupported screencap geometry or format " + format);
            }
            int bytes = sourceWidth * sourceHeight * 4;
            if (pixels == null || pixels.length != bytes) pixels = new byte[bytes];
            readFully(in, pixels);
            if (!process.waitFor(1, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException("screencap failed to finish");
            }
            lastSource = "screencap display=" + sourceWidth + "x" + sourceHeight;
            int width = Math.min(sourceWidth, targetWidth);
            int height = Math.max(1, (int) ((long) sourceHeight * width / sourceWidth));
            return new Frame(Pixels.sample(pixels, sourceWidth, sourceHeight, width, height), width, height);
        } finally {
            timeout.cancel(false);
            process.getInputStream().close();
            process.getErrorStream().close();
            process.destroy();
            current = null;
        }
    }

    public String describe() {
        return lastSource;
    }

    public void close() {
        Process process = current;
        if (process != null) process.destroy();
        watchdog.shutdownNow();
    }

    private static void readFully(InputStream in, byte[] bytes) throws IOException {
        int n = 0;
        while (n < bytes.length) {
            int read = in.read(bytes, n, bytes.length - n);
            if (read < 0) throw new EOFException("Incomplete screencap frame");
            n += read;
        }
    }
}
