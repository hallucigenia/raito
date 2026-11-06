// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.util.LinkedHashMap;
import java.util.Map;

/** Device identity for compatibility reports. Never includes serial numbers or account data. */
final class DeviceInfo {
    private static final String[][] BUILD_FIELDS = {
        {"manufacturer", "MANUFACTURER"}, {"brand", "BRAND"}, {"model", "MODEL"}, {"device", "DEVICE"},
        {"hardware", "HARDWARE"}, {"board", "BOARD"}, {"soc_manufacturer", "SOC_MANUFACTURER"},
        {"soc_model", "SOC_MODEL"}, {"build", "DISPLAY"},
    };

    private DeviceInfo() { }

    /** Android API level, or -1 when not running on Android. */
    static int sdkInt() {
        try {
            return Class.forName("android.os.Build$VERSION").getField("SDK_INT").getInt(null);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    static Map<String, Object> describe() {
        Map<String, Object> info = new LinkedHashMap<String, Object>();
        info.put("sdk", sdkInt());
        putField(info, "release", "android.os.Build$VERSION", "RELEASE");
        for (String[] field : BUILD_FIELDS) putField(info, field[0], "android.os.Build", field[1]);
        return info;
    }

    private static void putField(Map<String, Object> info, String key, String className, String fieldName) {
        try {
            Object value = Class.forName(className).getField(fieldName).get(null);
            if (value != null) info.put(key, value.toString());
        } catch (ReflectiveOperationException e) {
            // Field absent on this API level (for example SOC_MODEL before Android 12).
        }
    }
}
