// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;

/**
 * Files shared with the device launcher: {@code raito.lock} (single instance), {@code pid}
 * (written only by the lock holder) and {@code status} (rewritten after acknowledged frames;
 * its modification time is the health heartbeat).
 */
final class StateDir {
    static final String LOCK_FILE = "raito.lock";
    static final String PID_FILE = "pid";
    static final String STATUS_FILE = "status";

    private final File dir;

    StateDir(String path) {
        this.dir = new File(path);
    }

    Closeable lock() throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create state dir " + dir);
        File file = new File(dir, LOCK_FILE);
        final RandomAccessFile handle = new RandomAccessFile(file, "rw");
        final FileLock lock;
        try {
            lock = handle.getChannel().tryLock();
        } catch (IOException | RuntimeException e) {
            handle.close();
            throw e;
        }
        if (lock == null) {
            handle.close();
            throw new AlreadyRunningException(file.getPath());
        }
        return new Closeable() {
            public void close() throws IOException {
                try {
                    lock.release();
                } finally {
                    handle.close();
                }
            }
        };
    }

    void writePid() throws IOException {
        String pid = new File("/proc/self").getCanonicalFile().getName();
        if (!pid.matches("[0-9]+")) throw new IOException("Cannot determine own pid from /proc/self");
        writeAtomically(PID_FILE, pid + "\n");
    }

    void clearStatus() throws IOException {
        File status = new File(dir, STATUS_FILE);
        if (status.exists() && !status.delete()) throw new IOException("Cannot remove stale " + status);
    }

    void writeStatus(String text) throws IOException {
        writeAtomically(STATUS_FILE, text);
    }

    private void writeAtomically(String name, String text) throws IOException {
        File target = new File(dir, name);
        File next = new File(dir, name + ".next");
        try (FileOutputStream out = new FileOutputStream(next)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (!next.renameTo(target)) throw new IOException("Cannot replace " + target);
    }
}
