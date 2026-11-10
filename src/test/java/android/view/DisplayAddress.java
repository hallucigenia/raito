// SPDX-License-Identifier: Apache-2.0
package android.view;

/** Test stub. */
public abstract class DisplayAddress {
    public static final class Physical extends DisplayAddress {
        private final long id;

        public Physical(long id) {
            this.id = id;
        }

        public long getPhysicalDisplayId() { return id; }
    }

    public static final class Network extends DisplayAddress {
    }
}
