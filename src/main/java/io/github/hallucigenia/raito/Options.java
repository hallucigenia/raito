// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code --key value} command-line parsing shared by the commands. Dashes in keys become underscores. */
final class Options {
    private Options() { }

    static Map<String, String> parse(String[] args) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (!flag.startsWith("--") || flag.length() == 2) {
                throw new IllegalArgumentException("Unexpected argument " + flag);
            }
            if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + flag);
            String key = flag.substring(2).replace('-', '_');
            if (values.containsKey(key)) throw new IllegalArgumentException("Duplicate option " + flag);
            values.put(key, args[++i]);
        }
        return values;
    }

    static int integer(Map<String, String> values, String key, int min, int max) {
        String text = values.get(key);
        int value;
        try {
            value = Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole number, got '" + text + "'");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max + ", got " + value);
        }
        return value;
    }
}
