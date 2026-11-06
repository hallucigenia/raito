// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/** Dependency-free test runner: runs every public no-argument void method of each named class. */
public final class TestRunner {
    private TestRunner() { }

    public static void main(String[] args) throws Exception {
        int passed = 0;
        int failed = 0;
        for (String name : args) {
            Class<?> type = Class.forName(name);
            Method[] methods = type.getDeclaredMethods();
            Arrays.sort(methods, new Comparator<Method>() {
                public int compare(Method a, Method b) { return a.getName().compareTo(b.getName()); }
            });
            for (Method method : methods) {
                if (!isTest(method)) continue;
                try {
                    method.invoke(type.getDeclaredConstructor().newInstance());
                    passed++;
                } catch (InvocationTargetException e) {
                    failed++;
                    System.out.println("FAIL " + type.getSimpleName() + "." + method.getName()
                        + ": " + e.getCause());
                }
            }
        }
        System.out.println("tests passed=" + passed + " failed=" + failed);
        if (failed > 0) System.exit(1);
    }

    private static boolean isTest(Method method) {
        int modifiers = method.getModifiers();
        return Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)
            && method.getParameterTypes().length == 0 && method.getReturnType() == void.class;
    }
}
