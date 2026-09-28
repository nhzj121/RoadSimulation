package org.example.roadsimulation.sandbox.scenario;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Stable JSON and SHA-256 operations for {@link ScenarioSnapshot}. */
public final class ScenarioSnapshotCodec {

    private final ScenarioSnapshotCanonicalizer canonicalizer;
    private final ScenarioSnapshotValidator validator;
    private final ObjectMapper objectMapper;

    public ScenarioSnapshotCodec() {
        this(new ScenarioSnapshotCanonicalizer(), new ScenarioSnapshotValidator(), canonicalMapper());
    }

    ScenarioSnapshotCodec(
            ScenarioSnapshotCanonicalizer canonicalizer,
            ScenarioSnapshotValidator validator,
            ObjectMapper objectMapper
    ) {
        this.canonicalizer = canonicalizer;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public ScenarioSnapshot canonicalize(ScenarioSnapshot snapshot) {
        validator.validate(snapshot);
        ScenarioSnapshot canonical = canonicalizer.canonicalize(snapshot);
        validator.validate(canonical);
        return canonical;
    }

    public String toCanonicalJson(ScenarioSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(canonicalize(snapshot));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize ScenarioSnapshot", exception);
        }
    }

    public ScenarioSnapshot fromJson(String json) {
        try {
            return canonicalize(objectMapper.readValue(json, ScenarioSnapshot.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot deserialize ScenarioSnapshot", exception);
        }
    }

    public String fingerprint(ScenarioSnapshot snapshot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = toCanonicalJson(snapshot).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public void verifyFingerprint(ScenarioSnapshot snapshot, String expectedFingerprint) {
        if (expectedFingerprint == null || !expectedFingerprint.matches("(?i)[0-9a-f]{64}")) {
            throw new ScenarioSnapshotIntegrityException("Expected fingerprint is not a SHA-256 hex value");
        }
        String expected = expectedFingerprint.toLowerCase(Locale.ROOT);
        String actual = fingerprint(snapshot);
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)
        );
        if (!matches) {
            throw new ScenarioSnapshotIntegrityException(
                    "ScenarioSnapshot fingerprint mismatch: expected=" + expected + ", actual=" + actual
            );
        }
    }

    private static ObjectMapper canonicalMapper() {
        return JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .build();
    }
}
