package ru.practicum.moviehub.http;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class QueryString {

    private QueryString() {
    }

    static Map<String, String> parse(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String key = decode(kv[0]);
            String value = kv.length > 1 ? decode(kv[1]) : "";
            result.put(key, value);
        }

        return result;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
