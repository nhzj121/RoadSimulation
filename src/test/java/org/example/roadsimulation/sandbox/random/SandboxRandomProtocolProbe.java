package org.example.roadsimulation.sandbox.random;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/** Separate-JVM probe used by the protocol compatibility test. */
public final class SandboxRandomProtocolProbe {
    private SandboxRandomProtocolProbe() {}

    public static void main(String[] args) {
        SandboxRandomProtocol protocol = new SandboxRandomProtocol(
                new ObjectMapper().findAndRegisterModules());
        System.out.print(protocol.deriveSeedHex(
                "20260927",
                SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY,
                Map.of("loopIndex", 6, "processingChainId", 5001L)));
    }
}
