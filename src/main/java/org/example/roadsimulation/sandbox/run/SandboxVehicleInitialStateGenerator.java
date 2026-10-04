package org.example.roadsimulation.sandbox.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1;
import org.example.roadsimulation.sandbox.random.SandboxRandomDomain;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;
import org.example.roadsimulation.sandbox.scenario.definition.EffectiveScenarioData;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves every scenario vehicle to one deterministic initial POI. */
public final class SandboxVehicleInitialStateGenerator {
    private static final Set<String> ELIGIBLE_TYPES = Set.of("WAREHOUSE", "DISTRIBUTION_CENTER");

    private final SandboxRandomProtocol randomProtocol;

    public SandboxVehicleInitialStateGenerator(ObjectMapper objectMapper) {
        this.randomProtocol = new SandboxRandomProtocol(objectMapper);
    }

    public List<SandboxVehicleInitialState> generate(EffectiveScenarioData scenario, String rootSeed) {
        Map<Long, Long> fixed = scenario.fixedVehiclePois();
        validateFixedPois(scenario, fixed);
        List<SandboxBaselinePackageV1.Poi> candidates = eligibleCandidates(scenario);
        boolean randomVehicleExists = scenario.data().vehicles().stream()
                .anyMatch(vehicle -> !fixed.containsKey(vehicle.id()));
        if (randomVehicleExists && candidates.isEmpty()) {
            throw new SandboxRunException(
                    "NO_VEHICLE_INITIALIZATION_POI", "Scenario has no eligible vehicle initialization POI");
        }

        return scenario.data().vehicles().stream()
                .sorted(Comparator.comparingLong(SandboxBaselinePackageV1.Vehicle::id))
                .map(vehicle -> resolve(vehicle.id(), fixed, candidates, rootSeed))
                .toList();
    }

    private void validateFixedPois(EffectiveScenarioData scenario, Map<Long, Long> fixed) {
        Map<Long, SandboxBaselinePackageV1.Poi> pois = new HashMap<>();
        scenario.data().pois().forEach(poi -> pois.put(poi.id(), poi));
        for (Map.Entry<Long, Long> entry : fixed.entrySet()) {
            SandboxBaselinePackageV1.Poi poi = pois.get(entry.getValue());
            if (poi == null
                    || !ELIGIBLE_TYPES.contains(poi.poiType())
                    || poi.longitude() == null
                    || poi.latitude() == null) {
                throw new SandboxRunException(
                        "INVALID_FIXED_VEHICLE_POI",
                        "Fixed vehicle POI must be a selected warehouse or distribution center with coordinates: vehicleId="
                                + entry.getKey() + ", poiId=" + entry.getValue());
            }
        }
    }

    public int eligibleCandidateCount(EffectiveScenarioData scenario) {
        return eligibleCandidates(scenario).size();
    }

    private List<SandboxBaselinePackageV1.Poi> eligibleCandidates(EffectiveScenarioData scenario) {
        return scenario.data().pois().stream()
                .filter(poi -> ELIGIBLE_TYPES.contains(poi.poiType()))
                .filter(poi -> poi.longitude() != null && poi.latitude() != null)
                .sorted(Comparator.comparingLong(SandboxBaselinePackageV1.Poi::id))
                .toList();
    }

    private SandboxVehicleInitialState resolve(
            long vehicleId,
            Map<Long, Long> fixed,
            List<SandboxBaselinePackageV1.Poi> candidates,
            String rootSeed
    ) {
        Long fixedPoi = fixed.get(vehicleId);
        if (fixedPoi != null) {
            return new SandboxVehicleInitialState(
                    vehicleId, SandboxVehicleInitialState.FIXED_POI, fixedPoi,
                    null, null, null);
        }

        Map<String, Object> key = Map.of("vehicleId", vehicleId);
        int selected = randomProtocol.random(rootSeed, SandboxRandomDomain.VEHICLE_INITIAL_POI, key)
                .nextInt(candidates.size());
        return new SandboxVehicleInitialState(
                vehicleId,
                SandboxVehicleInitialState.RANDOM_DERIVED_POI,
                candidates.get(selected).id(),
                SandboxRandomDomain.VEHICLE_INITIAL_POI.id(),
                randomProtocol.canonicalBusinessKey(key),
                randomProtocol.deriveSeedHex(rootSeed, SandboxRandomDomain.VEHICLE_INITIAL_POI, key));
    }
}
