package org.example.roadsimulation.sandbox.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.sandbox.baseline.LexicographicJsonSha256;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Execution-only numeric normalization; never changes baseline/random protocol hashing. */
public final class SandboxExecutionJson {
    public static final String VERSION = "sandbox-execution-json/v1";
    private final ObjectMapper json;
    private final LexicographicJsonSha256 canonical;
    public SandboxExecutionJson(ObjectMapper json) { this.json = json; canonical = new LexicographicJsonSha256(json); }
    public String hash(JsonNode value) { return canonical.hash(normalize(value)); }
    public byte[] canonicalBytes(JsonNode value) { return canonical.canonicalBytes(normalize(value)); }

    private JsonNode normalize(JsonNode value) {
        if (value.isFloatingPointNumber()) {
            SandboxBusinessFactCapture.requireFinite(value);
            return DecimalNode.valueOf(value.decimalValue().stripTrailingZeros());
        }
        if (value.isObject()) {
            ObjectNode result = json.createObjectNode();
            value.fields().forEachRemaining(field -> result.set(field.getKey(), normalize(field.getValue())));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = json.createArrayNode(); value.forEach(item -> result.add(normalize(item))); return result;
        }
        return value.deepCopy();
    }

    /** Opaque manifest preserves authenticated legacy artifacts' original numeric spellings. */
    public static String hashManifestText(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
