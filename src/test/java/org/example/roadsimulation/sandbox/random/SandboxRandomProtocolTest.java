package org.example.roadsimulation.sandbox.random;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxRandomProtocolTest {
    private final SandboxRandomProtocol protocol = new SandboxRandomProtocol(
            new ObjectMapper().findAndRegisterModules());

    @Test
    void derivesStableDomainSeparatedSeedsFromCanonicalBusinessKeys() {
        Map<String, Object> firstOrder = new LinkedHashMap<>();
        firstOrder.put("loopIndex", 6);
        firstOrder.put("processingChainId", 5001L);
        Map<String, Object> reverseOrder = new LinkedHashMap<>();
        reverseOrder.put("processingChainId", 5001L);
        reverseOrder.put("loopIndex", 6);

        long first = protocol.deriveSeed("20260927", SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY, firstOrder);
        long repeated = protocol.deriveSeed("20260927", SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY, reverseOrder);
        long otherDomain = protocol.deriveSeed("20260927", SandboxRandomDomain.PRODUCTION_SINK_POI, firstOrder);
        long otherKey = protocol.deriveSeed(
                "20260927",
                SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY,
                Map.of("loopIndex", 7, "processingChainId", 5001L));

        assertEquals(first, repeated);
        assertNotEquals(first, otherDomain);
        assertNotEquals(first, otherKey);
        assertEquals("103b99c86509bec7", Long.toHexString(first));
    }

    @Test
    void splitMixHasStableGoldenVectorAndValidBounds() {
        SplitMix64V1 random = new SplitMix64V1(0L);
        assertEquals("e220a8397b1dcdaf", Long.toHexString(random.nextLong()));
        assertEquals("6e789e6aa1b965f4", Long.toHexString(random.nextLong()));

        SplitMix64V1 bounded = new SplitMix64V1(1234L);
        for (int i = 0; i < 10_000; i++) {
            int value = bounded.nextInt(7);
            assertTrue(value >= 0 && value < 7);
        }
        assertThrows(IllegalArgumentException.class, () -> bounded.nextInt(0));
    }

    @Test
    void derivesTheSameGoldenSeedInAFreshJvm() throws Exception {
        String javaExecutable = Path.of(
                System.getProperty("java.home"), "bin", "java.exe").toString();
        Process process = new ProcessBuilder(
                javaExecutable,
                "-cp",
                System.getProperty("java.class.path"),
                SandboxRandomProtocolProbe.class.getName())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();

        assertEquals(0, process.waitFor(), output);
        assertEquals("103b99c86509bec7", output);
    }

    @Test
    void rejectsNonCanonicalOrOverflowingRootSeed() {
        assertThrows(IllegalArgumentException.class, () -> SandboxRandomProtocol.validateRootSeed(null));
        assertThrows(IllegalArgumentException.class, () -> SandboxRandomProtocol.validateRootSeed("-1"));
        assertThrows(IllegalArgumentException.class, () -> SandboxRandomProtocol.validateRootSeed("01"));
        assertThrows(IllegalArgumentException.class,
                () -> SandboxRandomProtocol.validateRootSeed("9223372036854775808"));
        assertEquals(0L, SandboxRandomProtocol.validateRootSeed("0"));
        assertEquals(Long.MAX_VALUE, SandboxRandomProtocol.validateRootSeed(Long.toString(Long.MAX_VALUE)));
    }
}
