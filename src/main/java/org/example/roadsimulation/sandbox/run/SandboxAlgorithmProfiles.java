package org.example.roadsimulation.sandbox.run;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registry of the only algorithm profiles accepted by run-specification v1. */
public final class SandboxAlgorithmProfiles {
    public static final String ORIGINAL_V1 = "original-vrp/v1";
    public static final String HEURISTIC_V1 = "heuristic-multi-order-ga/v1";

    public SandboxAlgorithmProfile resolve(String strategy, String profileId) {
        SandboxAlgorithmProfile profile = switch (profileId == null ? "" : profileId) {
            case ORIGINAL_V1 -> original();
            case HEURISTIC_V1 -> heuristic();
            default -> throw new SandboxRunException(
                    "UNKNOWN_ALGORITHM_PROFILE", "Unknown algorithm profile: " + profileId);
        };
        if (!profile.strategy().equals(strategy)) {
            throw new SandboxRunException(
                    "ALGORITHM_PROFILE_STRATEGY_MISMATCH",
                    "Algorithm profile " + profileId + " does not belong to strategy " + strategy);
        }
        return profile;
    }

    private SandboxAlgorithmProfile original() {
        return new SandboxAlgorithmProfile(ORIGINAL_V1, "ORIGINAL", map(
                "minLoadFactor", 0.80,
                "maxAnchorDistanceKm", 400.0,
                "maxMarginalCost", 4000.0,
                "minAddedTonsPerExtraKm", 0.02));
    }

    private SandboxAlgorithmProfile heuristic() {
        Map<String, Object> ga = map(
                "populationSize", 20,
                "maxGeneration", 30,
                "noImproveLimit", 8,
                "eliteRatio", 0.10,
                "crossoverRate", 0.85,
                "mutationRate", 0.25,
                "tournamentSize", 5);
        Map<String, Object> initial = map(
                // MultiOrderGA historically overrides this with ga.populationSize.
                // Persist the effective value instead of the unused class default.
                "populationSize", 20,
                "greedyEliteCount", 10,
                "perturbedGreedyCount", 40,
                "randomizedGreedyCount", 30,
                "topKInsertionChoice", 5,
                "perturbRatio", 0.15);
        Map<String, Object> mutation = map(
                "singleReinsertProbability", 0.45,
                "destroyRepairProbability", 0.35,
                "routeShakeProbability", 0.20,
                "maxDestroyCount", 5,
                "maxRouteShakeCount", 4,
                "topKInsertionChoice", 3,
                "maxInsertionScore", 50.0,
                "insertionWaitingRelaxFactor", 0.10,
                "bestInsertionProbability", 0.85);
        Map<String, Object> cost = map(
                "hardConstraintPenalty", 1_000_000.0,
                "duplicateAssignmentPenalty", 500_000.0,
                "missingLoadUnloadPenalty", 500_000.0,
                "lifoViolationPenalty", 500_000.0,
                "overloadPenalty", 500_000.0,
                "overVolumePenalty", 500_000.0,
                "distanceWeight", 0.35,
                "emptyDistanceWeight", 0.15,
                "capacityWasteWeight", 0.25,
                "vehicleCountWeight", 0.15,
                "lowUtilizationWeight", 0.10,
                "unassignedBasePenalty", 20.0,
                "unassignedWaitingHourPenalty", 5.0,
                "unassignedPriorityPenalty", 10.0,
                "distanceNormKm", 100.0,
                "emptyDistanceNormKm", 50.0,
                "capacityWasteNormTonKm", 500.0,
                "vehicleCountNorm", 10.0,
                "lowUtilNorm", 10.0,
                "idealUtilization", 0.50);
        return new SandboxAlgorithmProfile(HEURISTIC_V1, "HEURISTIC", map(
                "ga", ga,
                "initialPopulation", initial,
                "mutation", mutation,
                "costNormalization", cost));
    }

    private Map<String, Object> map(Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }
}
