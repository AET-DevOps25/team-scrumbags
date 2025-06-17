package com.trace.sdlc_connector;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    static Pattern arrayRegexPattern = Pattern.compile("((?:\\w+\\.)*)(\\w+)\\[\\*](.*)");

    public static Map<String, Object> extractWithOneArray(DocumentContext json, String arrayPath) {
        if (!arrayPath.startsWith("$.")) {
            throw new IllegalArgumentException("Array path must start with '$.': " + arrayPath);
        }

        List<String> values = json.read(arrayPath);
        arrayPath = arrayPath.substring(2); // Remove the '$.' prefix

        Matcher matcher = arrayRegexPattern.matcher(arrayPath);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid array path format: " + arrayPath);
        }

        Map<String, Object> result = new HashMap<>();

        // iterate until array segment
        Map<String, Object> current = result;
        String[] segments = matcher.group(1).split("\\.");
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].isEmpty()) {
                continue;
            }

            current.put(segments[i], new HashMap<String, Object>());
            current = (Map<String, Object>) current.get(segments[i]);
        }

        // extract array segment
        current.put(matcher.group(2), new ArrayList<Object>());
        var list = (ArrayList<Object>) current.get(matcher.group(2));

        var afterArrayPart = matcher.group(3);
        if (!StringUtils.hasText(afterArrayPart)) {
            // case that the last capture group is empty
            list.addAll(values);
        } else {
            afterArrayPart = afterArrayPart.substring(1); // remove the leading '.'
            segments = afterArrayPart.split("\\.");
            // build Map for rest of the segments
            for (Object value : values) {
                current = new HashMap<>();
                list.add(current);
                for (int i = 0; i < segments.length - 1; i++) {
                    if (segments[i].isEmpty()) {
                        continue;
                    }

                    current.put(segments[i], new HashMap<String, Object>());
                    current = (Map<String, Object>) current.get(segments[i]);
                }

                current.put(segments[segments.length - 1], value);
            }
        }

        return result;
    }
}
