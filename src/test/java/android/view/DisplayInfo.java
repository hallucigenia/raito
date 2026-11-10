// SPDX-License-Identifier: Apache-2.0
package android.view;

/** Test stub. */
public final class DisplayInfo {
    public int logicalWidth;
    public int logicalHeight;
    public DisplayAddress address;

    public DisplayInfo(int logicalWidth, int logicalHeight, DisplayAddress address) {
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.address = address;
    }
}
