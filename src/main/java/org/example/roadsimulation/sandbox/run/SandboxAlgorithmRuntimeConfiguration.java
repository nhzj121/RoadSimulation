package org.example.roadsimulation.sandbox.run;

import org.example.roadsimulation.optimizer.multi.cost.CostNormalizationConfig;
import org.example.roadsimulation.optimizer.multi.ga.MultiOrderGAConfig;
import org.example.roadsimulation.optimizer.multi.ga.MutationConfig;
import org.example.roadsimulation.optimizer.multi.init.InitialPopulationConfig;
import org.example.roadsimulation.service.OriginalVrpDispatchPolicy;

import java.util.Map;

/** Converts an immutable published profile snapshot into runtime config objects. */
public final class SandboxAlgorithmRuntimeConfiguration {
    private SandboxAlgorithmRuntimeConfiguration() {}

    public static void applyOriginal(
            SandboxAlgorithmProfile profile,
            OriginalVrpDispatchPolicy policy
    ) {
        requireProfile(profile, SandboxAlgorithmProfiles.ORIGINAL_V1, "ORIGINAL");
        Map<String, Object> values = profile.parameters();
        policy.setMinLoadFactor(number(values, "minLoadFactor").doubleValue());
        policy.setMaxAnchorDistanceKm(number(values, "maxAnchorDistanceKm").doubleValue());
        policy.setMaxMarginalCost(number(values, "maxMarginalCost").doubleValue());
        policy.setMinAddedTonsPerExtraKm(number(values, "minAddedTonsPerExtraKm").doubleValue());
        policy.freezeForSandbox();
    }

    public static HeuristicConfiguration heuristic(SandboxAlgorithmProfile profile) {
        requireProfile(profile, SandboxAlgorithmProfiles.HEURISTIC_V1, "HEURISTIC");
        Map<String, Object> gaValues = nested(profile.parameters(), "ga");
        Map<String, Object> initialValues = nested(profile.parameters(), "initialPopulation");
        Map<String, Object> mutationValues = nested(profile.parameters(), "mutation");
        Map<String, Object> costValues = nested(profile.parameters(), "costNormalization");

        MultiOrderGAConfig ga = new MultiOrderGAConfig();
        ga.setPopulationSize(integer(gaValues, "populationSize"));
        ga.setMaxGeneration(integer(gaValues, "maxGeneration"));
        ga.setNoImproveLimit(integer(gaValues, "noImproveLimit"));
        ga.setEliteRatio(decimal(gaValues, "eliteRatio"));
        ga.setCrossoverRate(decimal(gaValues, "crossoverRate"));
        ga.setMutationRate(decimal(gaValues, "mutationRate"));
        ga.setTournamentSize(integer(gaValues, "tournamentSize"));

        InitialPopulationConfig initial = new InitialPopulationConfig();
        initial.setPopulationSize(integer(initialValues, "populationSize"));
        initial.setGreedyEliteCount(integer(initialValues, "greedyEliteCount"));
        initial.setPerturbedGreedyCount(integer(initialValues, "perturbedGreedyCount"));
        initial.setRandomizedGreedyCount(integer(initialValues, "randomizedGreedyCount"));
        initial.setTopKInsertionChoice(integer(initialValues, "topKInsertionChoice"));
        initial.setPerturbRatio(decimal(initialValues, "perturbRatio"));
        if (initial.getPopulationSize() != ga.getPopulationSize()) {
            throw new SandboxRunException(
                    "ALGORITHM_PROFILE_INCONSISTENT",
                    "GA and initial-population sizes must match in a frozen sandbox profile");
        }

        MutationConfig mutation = new MutationConfig();
        mutation.setSingleReinsertProbability(decimal(mutationValues, "singleReinsertProbability"));
        mutation.setDestroyRepairProbability(decimal(mutationValues, "destroyRepairProbability"));
        mutation.setRouteShakeProbability(decimal(mutationValues, "routeShakeProbability"));
        mutation.setMaxDestroyCount(integer(mutationValues, "maxDestroyCount"));
        mutation.setMaxRouteShakeCount(integer(mutationValues, "maxRouteShakeCount"));
        mutation.setTopKInsertionChoice(integer(mutationValues, "topKInsertionChoice"));
        mutation.setMaxInsertionScore(decimal(mutationValues, "maxInsertionScore"));
        mutation.setInsertionWaitingRelaxFactor(decimal(mutationValues, "insertionWaitingRelaxFactor"));
        mutation.setBestInsertionProbability(decimal(mutationValues, "bestInsertionProbability"));

        CostNormalizationConfig cost = new CostNormalizationConfig();
        cost.setHardConstraintPenalty(decimal(costValues, "hardConstraintPenalty"));
        cost.setDuplicateAssignmentPenalty(decimal(costValues, "duplicateAssignmentPenalty"));
        cost.setMissingLoadUnloadPenalty(decimal(costValues, "missingLoadUnloadPenalty"));
        cost.setLifoViolationPenalty(decimal(costValues, "lifoViolationPenalty"));
        cost.setOverloadPenalty(decimal(costValues, "overloadPenalty"));
        cost.setOverVolumePenalty(decimal(costValues, "overVolumePenalty"));
        cost.setDistanceWeight(decimal(costValues, "distanceWeight"));
        cost.setEmptyDistanceWeight(decimal(costValues, "emptyDistanceWeight"));
        cost.setCapacityWasteWeight(decimal(costValues, "capacityWasteWeight"));
        cost.setVehicleCountWeight(decimal(costValues, "vehicleCountWeight"));
        cost.setLowUtilizationWeight(decimal(costValues, "lowUtilizationWeight"));
        cost.setUnassignedBasePenalty(decimal(costValues, "unassignedBasePenalty"));
        cost.setUnassignedWaitingHourPenalty(decimal(costValues, "unassignedWaitingHourPenalty"));
        cost.setUnassignedPriorityPenalty(decimal(costValues, "unassignedPriorityPenalty"));
        cost.setDistanceNormKm(decimal(costValues, "distanceNormKm"));
        cost.setEmptyDistanceNormKm(decimal(costValues, "emptyDistanceNormKm"));
        cost.setCapacityWasteNormTonKm(decimal(costValues, "capacityWasteNormTonKm"));
        cost.setVehicleCountNorm(decimal(costValues, "vehicleCountNorm"));
        cost.setLowUtilNorm(decimal(costValues, "lowUtilNorm"));
        cost.setIdealUtilization(decimal(costValues, "idealUtilization"));

        return new HeuristicConfiguration(ga, initial, cost, mutation);
    }

    private static void requireProfile(
            SandboxAlgorithmProfile profile,
            String profileId,
            String strategy
    ) {
        if (profile == null || !profileId.equals(profile.profileId())
                || !strategy.equals(profile.strategy())) {
            throw new SandboxRunException(
                    "ALGORITHM_PROFILE_STRATEGY_MISMATCH",
                    "Published algorithm profile does not match the selected strategy");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            throw new SandboxRunException("INVALID_ALGORITHM_PROFILE", "Missing profile section: " + key);
        }
        return (Map<String, Object>) map;
    }

    private static Number number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new SandboxRunException("INVALID_ALGORITHM_PROFILE", "Missing numeric parameter: " + key);
        }
        return number;
    }

    private static int integer(Map<String, Object> values, String key) {
        return number(values, key).intValue();
    }

    private static double decimal(Map<String, Object> values, String key) {
        return number(values, key).doubleValue();
    }

    public record HeuristicConfiguration(
            MultiOrderGAConfig ga,
            InitialPopulationConfig initialPopulation,
            CostNormalizationConfig costNormalization,
            MutationConfig mutation
    ) {}
}
