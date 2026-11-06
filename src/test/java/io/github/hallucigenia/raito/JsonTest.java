// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonTest {
    public void writes_nested_values_in_insertion_order() {
        Map<String, Object> inner = new LinkedHashMap<String, Object>();
        inner.put("ok", true);
        inner.put("error", null);
        Map<String, Object> outer = new LinkedHashMap<String, Object>();
        outer.put("sdk", 30);
        outer.put("fps", 11.5);
        outer.put("results", Arrays.<Object>asList(inner, "x"));
        Check.equal("{\"sdk\":30,\"fps\":11.5,\"results\":[{\"ok\":true,\"error\":null},\"x\"]}",
            Json.write(outer), "json text");
    }

    public void escapes_quotes_backslashes_and_control_characters() {
        Check.equal("\"a\\\"b\\\\c\\nd\\u0001\"", Json.write("a\"b\\c\nd\u0001"), "escaped string");
    }

    public void writes_non_finite_numbers_as_null() {
        Check.equal("[null,null]", Json.write(Arrays.<Object>asList(Double.NaN, Double.POSITIVE_INFINITY)),
            "non-finite numbers");
    }
}
