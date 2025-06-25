package com.trace.sdlc_connector;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import java.util.*;

public class JsonUtils {
    /**
     * Extracts values from a JSON string based on the provided JSON paths.
     * The paths should be in the format of JSONPath, starting with '$.'.
     *
     * @param jsonString The JSON string to extract values from.
     * @param jsonPaths  The JSON paths to extract values for.
     * @return A map containing the extracted values, structured as nested objects based on the paths.
     */
    public static Map<String, Object> extract(String jsonString, String... jsonPaths) {
        var json = JsonPath.using(Configuration.builder()
                .options(Option.SUPPRESS_EXCEPTIONS).build()
        ).parse(jsonString);

        return extract(json, jsonPaths);
    }

    /**
     * Extracts values from a JSON document context based on the provided JSON paths.
     * The paths should be in the format of JSONPath, starting with '$.'.
     *
     * @param json      The DocumentContext representing the JSON document.
     * @param jsonPaths The JSON paths to extract values for.
     * @return A map containing the extracted values, structured as nested objects based on the paths.
     */
    public static Map<String, Object> extract(DocumentContext json, String... jsonPaths) {
        Map<String, Object> result = new HashMap<>();

        for (String path : jsonPaths) {
            if (!path.startsWith("$.")) {
                continue;
            }

            Object value = json.read(path);
            if (value == null) {
                continue;
            }

            String pathWithoutPrefix = path.substring(2);
            String[] segments = pathWithoutPrefix.split("\\.");

            Map<String, Object> current = result;
            for (int i = 0; i < segments.length - 1; i++) {
                String segment = segments[i];
                if (segment.isEmpty()) {
                    continue;
                }

                current.putIfAbsent(segment, new HashMap<String, Object>());
                current = (Map<String, Object>) current.get(segment);
            }

            // Add the value at the final level
            current.put(segments[segments.length - 1], value);
        }

        return result;
    }
}
