package com.trace.sdlc_connector;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;

public class JsonNodeUtils {
    public static Optional<String> textAt(JsonNode json, String path) {
        var node = json.at(path);
        if (node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }

        return Optional.of(node.asText());
    }

    public static void putTextAtInMap(JsonNode json, String path, Map<String, Object> map) {
        putTextAtInMap(json, path, map, path);
    }

    public static void putTextAtInMap(JsonNode json, String path, Map<String, Object> map, String key) {
        textAt(json, path).ifPresent(value -> map.put(key, value));
    }
}
