package org.example.roadsimulation.sandbox.baseline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Implements the baseline contract's {@code lexicographic-json-v1} canonicalization. */
public final class LexicographicJsonSha256 {

    // Tree serialization is protocol data, not a caller's display preference.
    // This writer uses compact UTF-8 JSON with no root wrapping or custom escaping.
    private static final ObjectWriter CANONICAL_WRITER = new ObjectMapper().writer();

    private final ObjectMapper objectMapper;

    public LexicographicJsonSha256(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(JsonNode value) {
        try {
            byte[] canonical = canonicalBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot calculate canonical JSON SHA-256", exception);
        }
    }

    public String hashObject(Object value) {
        return hash(objectMapper.valueToTree(value));
    }

    public byte[] canonicalBytes(JsonNode value) {
        try {
            return CANONICAL_WRITER.writeValueAsBytes(sortObjects(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot canonicalize JSON", exception);
        }
    }

    private JsonNode sortObjects(JsonNode value) {
        if (value.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            for (String name : names) {
                sorted.set(name, sortObjects(value.get(name)));
            }
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            value.forEach(element -> array.add(sortObjects(element)));
            return array;
        }
        return value.deepCopy();
    }
}
