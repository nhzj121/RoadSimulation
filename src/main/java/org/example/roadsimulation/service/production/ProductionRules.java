package org.example.roadsimulation.service.production;

import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.service.ProcessingChainGraphValidator;
import org.example.roadsimulation.service.ProductionTransportLoadSplitter.LoadPart;
import org.example.roadsimulation.sandbox.random.*;
import java.util.*;
import java.util.function.*;

/** Shared read-only calculations. No persistence, clocks, singleton state or network access.
 * Entity parameters are transient/read-only adapters, never retained in optimization artifacts. */
public final class ProductionRules {
    public static final String VERSION = "production-calculation/v1";
    public static final int MIN_FINAL_QUANTITY = 10;
    public static final int MAX_FINAL_QUANTITY = 34;
    private static final double EPSILON = 1e-9;
    private static final int NEAREST_CANDIDATE_COUNT = 5;
    private ProductionRules() {}
    public static int finalQuantity(IntUnaryOperator nextInt) {
        return nextInt.applyAsInt(MAX_FINAL_QUANTITY - MIN_FINAL_QUANTITY + 1) + MIN_FINAL_QUANTITY;
    }
    public static double round(double value) { return Math.round(value * 1000.0) / 1000.0; }
    public static double completedOutput(double input, Double ratio) {
        return round(input * (ratio == null ? 1.0 : ratio));
    }
    public static double distanceKm(POI from, POI to) {
        double lat1 = from.getLatitude().doubleValue(), lon1 = from.getLongitude().doubleValue();
        double lat2 = to.getLatitude().doubleValue(), lon2 = to.getLongitude().doubleValue();
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
    private static double outputRatio(ProcessingStage stage) {
        return stage.getOutputWeightRatio() == null ? 1.0 : stage.getOutputWeightRatio();
    }
    public record PlanCalculation(Map<ProcessingStage, Double> outputs,
                                  Map<ProcessingStage, Map<String, Double>> inputWeights) {
        public PlanCalculation {
            outputs = Collections.unmodifiableMap(new IdentityHashMap<>(outputs));
            var copy = new IdentityHashMap<ProcessingStage, Map<String, Double>>();
            inputWeights.forEach((key, value) -> copy.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(value))));
            inputWeights = Collections.unmodifiableMap(copy);
        }
    }
    @FunctionalInterface public interface RandomSource {
        SplitMix64V1 random(SandboxRandomDomain domain, Map<String, ?> key);
    }
    public static Map<ProcessingStage, POI> selectAutomatic(
            List<ProcessingStage> stages, List<ProcessingChainGraphValidator.EdgeSpec> edges,
            ProcessingStage sink, int loopIndex, long processingChainId,
            Function<ProcessingStage, List<POI>> candidates, ToDoubleBiFunction<POI,POI> distance,
            RandomSource random) {
        Map<ProcessingStage, POI> selected = new IdentityHashMap<>();
        selected.put(sink, choose(random,
                candidates.apply(sink),
                SandboxRandomDomain.PRODUCTION_SINK_POI,
                Map.of(
                        "loopIndex", loopIndex,
                        "processingChainId", processingChainId,
                        "sinkStageId", requireStageId(sink))));

        List<ProcessingStage> reverseOrder = new ArrayList<>(
                ProcessingChainGraphValidator.topologicalOrder(stages, edges));
        Collections.reverse(reverseOrder);
        for (ProcessingStage stage : reverseOrder) {
            if (stage == sink) {
                continue;
            }
            ProcessingChainGraphValidator.EdgeSpec outgoing = edges.stream()
                    .filter(edge -> edge.fromStage() == stage)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "非末端节点缺少下游节点: " + stage.getStageName()));
            POI downstream = selected.get(outgoing.toStage());
            if (downstream == null) {
                throw new IllegalStateException("下游节点尚未选择 POI: " + outgoing.toStage().getStageName());
            }
            List<POI> nearest = candidates.apply(stage).stream()
                    .filter(poi -> !poi.getId().equals(downstream.getId()))
                    .sorted(Comparator
                            .comparingDouble((POI poi) -> distance.applyAsDouble(poi, downstream))
                            .thenComparing(POI::getId))
                    .limit(NEAREST_CANDIDATE_COUNT)
                    .toList();
            selected.put(stage, choose(random,
                    nearest,
                    SandboxRandomDomain.PRODUCTION_STAGE_POI,
                    Map.of(
                            "loopIndex", loopIndex,
                            "processingChainId", processingChainId,
                            "stageId", requireStageId(stage))));
        }
        return selected;
    }

    public static PlanCalculation calculatePlan(
            List<ProcessingStage> stages,
            List<ProcessingChainGraphValidator.EdgeSpec> edges,
            List<ProcessingStage> topologicalOrder,
            ProcessingStage sink,
            double finalDemand
    ) {
        Map<ProcessingStage, Double> outputs = new IdentityHashMap<>();
        Map<ProcessingStage, Map<String, Double>> inputWeights = new IdentityHashMap<>();
        outputs.put(sink, finalDemand);

        List<ProcessingChainGraphValidator.InputSpec> sinkInputs =
                ProcessingChainGraphValidator.inputs(sink);
        if (sinkInputs.size() != 1) {
            throw new IllegalArgumentException("末端接收节点必须且只能接收一种最终阶段货物");
        }
        ProcessingChainGraphValidator.InputSpec sinkInput = sinkInputs.get(0);
        ProcessingChainGraphValidator.EdgeSpec finalEdge = requireIncomingEdge(
                edges, sink, sinkInput);
        inputWeights.put(sink, Map.of(sinkInput.inputKey(), finalDemand));
        outputs.merge(finalEdge.fromStage(), finalDemand, Double::sum);

        for (int i = topologicalOrder.size() - 1; i >= 0; i--) {
            ProcessingStage stage = topologicalOrder.get(i);
            if (stage == sink) {
                continue;
            }
            double output = outputs.getOrDefault(stage, 0.0);
            if (output <= 0 || !Double.isFinite(output)) {
                throw new IllegalStateException("工序需求量无效: " + stage.getStageName());
            }

            if (isSourceStage(stage, edges)) {
                validateCapacity(stage, output);
                inputWeights.put(stage, Map.of());
                continue;
            }

            double ratio = outputRatio(stage);
            double totalInput = round(output / ratio);
            validateCapacity(stage, totalInput);

            Map<String, Double> weights = new LinkedHashMap<>();
            for (ProcessingChainGraphValidator.InputSpec input :
                    ProcessingChainGraphValidator.inputs(stage)) {
                double weight = round(totalInput * input.inputShare());
                if (weight <= 0 || !Double.isFinite(weight)) {
                    throw new IllegalStateException("工序输入量无效: " + stage.getStageName());
                }
                weights.put(input.inputKey(), weight);
                ProcessingChainGraphValidator.EdgeSpec incoming =
                        requireIncomingEdge(edges, stage, input);
                outputs.merge(incoming.fromStage(), weight, Double::sum);
            }
            inputWeights.put(stage, weights);
        }
        return new PlanCalculation(outputs, inputWeights);
    }

    public static ProcessingChainGraphValidator.EdgeSpec requireIncomingEdge(
            List<ProcessingChainGraphValidator.EdgeSpec> edges,
            ProcessingStage stage,
            ProcessingChainGraphValidator.InputSpec input
    ) {
        return edges.stream()
                .filter(edge -> edge.toStage() == stage
                        && edge.input().inputKey().equals(input.inputKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "非起点节点输入缺少上游阶段: stage=" + stage.getStageName()
                                + ", input=" + input.inputKey()
                ));
    }

    private static boolean isSourceStage(
            ProcessingStage stage,
            List<ProcessingChainGraphValidator.EdgeSpec> edges
    ) {
        return edges.stream().noneMatch(edge -> edge.toStage() == stage);
    }

    private static void validateCapacity(ProcessingStage stage, double inputWeight) {
        if (stage.getMinBatchSize() != null && inputWeight < stage.getMinBatchSize()) {
            throw new IllegalStateException(
                    "反向计算输入量低于工序最小批量: stage=" + stage.getStageName()
                            + ", required=" + inputWeight
                            + ", minBatchSize=" + stage.getMinBatchSize()
            );
        }
        if (stage.getMaxCapacityPerCycle() != null && inputWeight > stage.getMaxCapacityPerCycle()) {
            throw new IllegalStateException(
                    "反向计算输入量超过工序单次产能: stage=" + stage.getStageName()
                            + ", required=" + inputWeight
                            + ", maxCapacityPerCycle=" + stage.getMaxCapacityPerCycle()
            );
        }
    }

    public static List<LoadPart> split(Goods goods, double totalWeight, List<Vehicle> fleet) {
        validateGoods(goods);
        if (totalWeight <= 0 || !Double.isFinite(totalWeight)) {
            throw new IllegalArgumentException("生产运输总重量必须大于 0");
        }

        double volumePerTonne = goods.getVolumePerUnit() / goods.getWeightPerUnit();
        double totalVolume = totalWeight * volumePerTonne;
        double maxPartWeight = maximumFeasibleWeight(preferredFleet(goods, fleet), volumePerTonne);
        if (maxPartWeight <= EPSILON) {
            maxPartWeight = maximumFeasibleWeight(fleet, volumePerTonne);
        }
        if (maxPartWeight <= EPSILON) {
            throw new IllegalStateException("没有车辆能够承载生产货物: " + goods.getSku());
        }

        int partCount = Math.max(1, (int) Math.ceil((totalWeight - EPSILON) / maxPartWeight));
        int totalQuantity = Math.max(partCount,
                (int) Math.max(1L, Math.round(totalWeight / goods.getWeightPerUnit())));

        double baseWeight = totalWeight / partCount;
        double baseVolume = totalVolume / partCount;
        int baseQuantity = totalQuantity / partCount;
        int quantityRemainder = totalQuantity % partCount;

        java.util.ArrayList<LoadPart> parts = new java.util.ArrayList<>(partCount);
        double assignedWeight = 0.0;
        double assignedVolume = 0.0;
        for (int index = 0; index < partCount; index++) {
            boolean last = index == partCount - 1;
            double partWeight = last ? totalWeight - assignedWeight : baseWeight;
            double partVolume = last ? totalVolume - assignedVolume : baseVolume;
            int quantity = baseQuantity + (index < quantityRemainder ? 1 : 0);
            parts.add(new LoadPart(partWeight, partVolume, quantity));
            assignedWeight += partWeight;
            assignedVolume += partVolume;
        }
        return List.copyOf(parts);
    }

    private static List<Vehicle> preferredFleet(Goods goods, List<Vehicle> fleet) {
        String vehicleFit = goods.getVehicleFit();
        if (vehicleFit == null || vehicleFit.isBlank() || fleet == null) {
            return List.of();
        }
        return fleet.stream()
                .filter(vehicle -> vehicle != null && vehicleFit.equals(vehicle.getVehicleType()))
                .toList();
    }

    private static double maximumFeasibleWeight(List<Vehicle> vehicles, double volumePerTonne) {
        if (vehicles == null || vehicles.isEmpty()) {
            return 0.0;
        }
        return vehicles.stream()
                .mapToDouble(vehicle -> feasibleWeight(vehicle, volumePerTonne))
                .max()
                .orElse(0.0);
    }

    private static double feasibleWeight(Vehicle vehicle, double volumePerTonne) {
        if (vehicle == null || vehicle.getMaxLoadCapacityTonnes() == null
                || vehicle.getMaxLoadCapacityTonnes() <= 0
                || !Double.isFinite(vehicle.getMaxLoadCapacityTonnes())) {
            return 0.0;
        }
        if (volumePerTonne <= EPSILON) {
            return vehicle.getMaxLoadCapacityTonnes();
        }
        Double cargoVolume = vehicle.getCargoVolume();
        if (cargoVolume == null || cargoVolume <= 0 || !Double.isFinite(cargoVolume)) {
            return 0.0;
        }
        return Math.min(vehicle.getMaxLoadCapacityTonnes(), cargoVolume / volumePerTonne);
    }

    private static void validateGoods(Goods goods) {
        if (goods == null || goods.getSku() == null || goods.getSku().isBlank()) {
            throw new IllegalArgumentException("生产运输货物缺少 Goods 主数据");
        }
        if (goods.getWeightPerUnit() == null || goods.getWeightPerUnit() <= 0
                || !Double.isFinite(goods.getWeightPerUnit())) {
            throw new IllegalStateException("生产运输货物单位重量无效: " + goods.getSku());
        }
        if (goods.getVolumePerUnit() == null || goods.getVolumePerUnit() < 0
                || !Double.isFinite(goods.getVolumePerUnit())) {
            throw new IllegalStateException("生产运输货物单位体积无效: " + goods.getSku());
        }
    }


    private static POI choose(RandomSource random, List<POI> candidates,
                              SandboxRandomDomain domain, Map<String, ?> key) {
        if (candidates.isEmpty()) throw new IllegalStateException("POI 候选集合不能为空");
        var ordered = candidates.stream().sorted(Comparator.comparing(POI::getId)).toList();
        return ordered.get(random.random(domain, key).nextInt(ordered.size()));
    }
    private static long requireStageId(ProcessingStage stage) {
        if (stage == null || stage.getId() == null) throw new IllegalStateException("processing stage id is required for sandbox randomness");
        return stage.getId();
    }
}
