package org.example.roadsimulation.sandbox.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.entity.CostEntity;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.CostBaselineNormalizationService;
import org.example.roadsimulation.service.GetCostService;
import org.example.roadsimulation.service.impl.POIShipmentManagerImpl;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Captures non-SQL facts after the same completed business tick, without advancing it. */
@Component
@Profile("sandbox-runtime")
public class SandboxRuntimeFactCollector {
    private final ObjectMapper json;
    private final VehicleRepository vehicles;
    private final AssignmentRepository assignments;
    private final GetCostService costs;
    private final CostBaselineNormalizationService normalization;
    private final POIShipmentManagerImpl pois;

    public SandboxRuntimeFactCollector(ObjectMapper json, VehicleRepository vehicles, AssignmentRepository assignments,
            GetCostService costs, CostBaselineNormalizationService normalization, POIShipmentManagerImpl pois) {
        this.json = json; this.vehicles = vehicles; this.assignments = assignments;
        this.costs = costs; this.normalization = normalization; this.pois = pois;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ObjectNode capture() {
        ObjectNode result = json.createObjectNode();
        result.set("costAccumulators", json.valueToTree(Map.of(
                "totalTransportTime", CostEntity.totalTransportTime, "totalMileage", CostEntity.totalMileage,
                "totalMileageWithoutThings", CostEntity.totalMileageWithoutThings, "totalWaitingTime", CostEntity.totalWaitingTime,
                "totalTheoryCapacity", CostEntity.totalTheoryCapacity, "totalRealityCapacity", CostEntity.totalRealityCapacity,
                "worstTheoryRealityCapacity", CostEntity.WorstTheoryRealityCapacity,
                "worstWaitingTransportTime", CostEntity.WorstWaitingTransportTime, "worstLoss", CostEntity.WorstLoss)));
        var orderedVehicles = vehicles.findAll().stream().sorted(java.util.Comparator.comparing(org.example.roadsimulation.entity.Vehicle::getId)).toList();
        var orderedAssignments = assignments.findRuntimeActiveAssignments().stream().sorted(java.util.Comparator.comparing(org.example.roadsimulation.entity.Assignment::getId)).toList();
        var runtimeCosts = costs.calculateRuntimeCosts(orderedVehicles, orderedAssignments);
        normalization.applyLatest(runtimeCosts);
        result.set("costs", json.valueToTree(runtimeCosts));
        result.set("normalizationBaseline", json.valueToTree(normalization.exportCurrentBaselineDetail()));
        result.set("normalizationWindow", json.valueToTree(normalization.exportLatestWindowDetail()));
        result.set("poiManager", json.valueToTree(pois.snapshotBusinessFacts()));
        return result;
    }
}
