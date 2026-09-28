package org.example.roadsimulation.sandbox.random;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.example.roadsimulation.sandbox.baseline.LexicographicJsonSha256;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** Implements the persisted {@code HMAC_SHA256_SPLITMIX64_V1} sandbox protocol. */
public final class SandboxRandomProtocol {
    public static final String PROTOCOL_ID = "HMAC_SHA256_SPLITMIX64_V1";
    private static final String ROOT_PREFIX = "road-sandbox-root/v1:";
    private static final String MESSAGE_VERSION = "road-sandbox-rng/v1";

    private final ObjectMapper objectMapper;
    private final LexicographicJsonSha256 canonicalizer;

    public SandboxRandomProtocol(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.canonicalizer = new LexicographicJsonSha256(objectMapper);
    }

    public long deriveSeed(String rootSeed, SandboxRandomDomain domain, Map<String, ?> stableBusinessKey) {
        validateRootSeed(rootSeed);
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(stableBusinessKey, "stableBusinessKey must not be null");

        ArrayNode message = objectMapper.createArrayNode();
        message.add(MESSAGE_VERSION);
        message.add(domain.id());
        JsonNode key = objectMapper.valueToTree(stableBusinessKey);
        if (!key.isObject()) {
            throw new IllegalArgumentException("stableBusinessKey must serialize as an object");
        }
        message.add(key);

        byte[] digest = hmac(rootKey(rootSeed), canonicalizer.canonicalBytes(message));
        return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
    }

    public String deriveSeedHex(String rootSeed, SandboxRandomDomain domain, Map<String, ?> stableBusinessKey) {
        return HexFormat.of().toHexDigits(deriveSeed(rootSeed, domain, stableBusinessKey));
    }

    public SplitMix64V1 random(String rootSeed, SandboxRandomDomain domain, Map<String, ?> stableBusinessKey) {
        return new SplitMix64V1(deriveSeed(rootSeed, domain, stableBusinessKey));
    }

    public String rootSeedFingerprint(String rootSeed) {
        validateRootSeed(rootSeed);
        return HexFormat.of().formatHex(rootKey(rootSeed));
    }

    public static long validateRootSeed(String rootSeed) {
        if (rootSeed == null || !rootSeed.matches("0|[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("rootSeed must be a non-negative decimal long string");
        }
        try {
            return Long.parseLong(rootSeed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("rootSeed exceeds Long.MAX_VALUE", exception);
        }
    }

    private byte[] rootKey(String rootSeed) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((ROOT_PREFIX + rootSeed).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot derive sandbox random seed", exception);
        }
    }
}
