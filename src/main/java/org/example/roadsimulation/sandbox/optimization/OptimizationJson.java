package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.example.roadsimulation.sandbox.baseline.LexicographicJsonSha256;
import java.io.IOException;
import java.io.OutputStream;

/** Optimization-only codec. Existing run, baseline and execution protocols are unchanged. */
public final class OptimizationJson {
    public static final String CANONICALIZATION = "sandbox-optimization-json/v1";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);

    private OptimizationJson() {}

    public static String write(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot serialize optimization contract", e); }
    }

    public static void write(OutputStream output, Object value) throws IOException {
        JSON.writeValue(output, value);
    }

    public static <T> T read(String value, Class<T> type) {
        try { return JSON.readValue(value, type); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid optimization contract", e); }
    }

    public static String fingerprint(Object value) {
        var payload = JSON.createObjectNode();
        payload.put("canonicalization", CANONICALIZATION);
        payload.set("value", canonical(JSON.valueToTree(value)));
        return new LexicographicJsonSha256(JSON).hash(payload);
    }

    private static JsonNode canonical(JsonNode node) {
        if (node.isFloatingPointNumber()) {
            // Decimal fields are strings only in the fingerprint payload, never in the wire contract.
            return TextNode.valueOf(node.decimalValue().stripTrailingZeros().toPlainString());
        }
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            node.fields().forEachRemaining(e -> result.set(e.getKey(), canonical(e.getValue())));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            node.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return node.deepCopy();
    }
}
