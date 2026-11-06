// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

/** Another Raito process holds the state directory lock. */
final class AlreadyRunningException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    AlreadyRunningException(String lockPath) {
        super("Another Raito process is already running (lock held on " + lockPath + ")");
    }
}
