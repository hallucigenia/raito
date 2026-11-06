// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reads {@code IPowerManager.isInteractive()} so capture pauses while the screen is off.
 * The binder is fetched again after any call failure: this process is not a zygote child and
 * outlives a system_server restart, which leaves the old proxy dead.
 */
final class DisplayPower implements Forwarder.Power {
    private final Method getService;
    private final Method asInterface;
    private final Method interactive;
    private Object service;

    DisplayPower() throws Exception {
        Class<?> binder = Class.forName("android.os.IBinder");
        getService = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
        asInterface = Class.forName("android.os.IPowerManager$Stub").getMethod("asInterface", binder);
        interactive = Class.forName("android.os.IPowerManager").getMethod("isInteractive");
        service = connect();
    }

    public boolean awake() throws Exception {
        if (service == null) service = connect();
        try {
            return (Boolean) interactive.invoke(service);
        } catch (InvocationTargetException e) {
            service = null;
            throw e;
        }
    }

    private Object connect() throws Exception {
        Object token = getService.invoke(null, "power");
        if (token == null) throw new IllegalStateException("Power service is unavailable");
        return asInterface.invoke(null, token);
    }
}
