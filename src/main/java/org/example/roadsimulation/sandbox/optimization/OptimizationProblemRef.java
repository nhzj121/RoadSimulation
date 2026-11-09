package org.example.roadsimulation.sandbox.optimization;

import java.time.LocalDateTime;
import java.util.Objects;

/** Caller must authenticate the frozen input fingerprint; G0 performs no database verification. */
public record OptimizationProblemRef(String problemFingerprint, LocalDateTime simulationStart,
                                     int totalLoops, long tickSeconds, String evaluationContractVersion) {
    public OptimizationProblemRef {
        problemFingerprint = ContractChecks.hash(problemFingerprint, "problemFingerprint");
        Objects.requireNonNull(simulationStart, "simulationStart");
        if (totalLoops <= 0 || tickSeconds <= 0) throw new IllegalArgumentException("Invalid simulation horizon");
        evaluationContractVersion = ContractChecks.text(evaluationContractVersion, "evaluationContractVersion");
        simulationStart.plusSeconds(Math.multiplyExact((long) totalLoops, tickSeconds));
    }

    public LocalDateTime deadline() {
        return simulationStart.plusSeconds(Math.multiplyExact((long) totalLoops, tickSeconds));
    }
}
