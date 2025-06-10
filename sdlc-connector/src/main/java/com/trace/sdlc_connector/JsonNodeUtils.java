package com.trace.sdlc_connector;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class JsonNodeUtils {

    /**
     * maps a Json Node field using the provided processor function.
     * returns null if the field is null or does not exist.
     *
     * @param json
     * @param fieldName
     * @param processor
     * @param <T>
     * @return
     */
    public static <T> T nullableMap(JsonNode json, String fieldName, Function<JsonNode, T> processor) {
        var value = json.get(fieldName);
        return value == null || value.isNull() ? null : processor.apply(value);
    }

    /**
     * applies a processor to a Json Node field or to null if the field is null or does not exist.
     *
     * @param json
     * @param fieldName
     * @param processor
     */
    public static void requiredNullableProcess(JsonNode json, String fieldName, Consumer<JsonNode> processor) {
        var value = JsonNodeUtils.nullableMap(json, fieldName, Function.identity());
        processor.accept(value);
    }

    /**
     * apply a processor to a field in a JsonNode if it exists, otherwise do nothing
     *
     * @param json
     * @param fieldName
     * @param processor
     */
    public static void optional(JsonNode json, String fieldName, Consumer<JsonNode> processor) {
        Optional.ofNullable(json.get(fieldName)).ifPresent(processor);
    }


}
