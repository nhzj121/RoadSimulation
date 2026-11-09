package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data;
import org.example.roadsimulation.service.ProcessingChainGraphValidator;
import java.util.*;

/** Transient, disconnected copies for existing graph validation and shared read-only calculations. */
final class ProductionFactsAdapter {
    final Map<Long, POI> pois = new TreeMap<>();
    final Map<String, Goods> goods = new TreeMap<>();
    final List<Vehicle> vehicles = new ArrayList<>();
    final List<ProcessingChain> chains = new ArrayList<>();

    ProductionFactsAdapter(Data data) {
        for (var fact : data.pois()) {
            var poi = new POI(); poi.setId(fact.id()); poi.setName(fact.name());
            poi.setLongitude(fact.longitude()); poi.setLatitude(fact.latitude());
            poi.setPoiType(POI.POIType.valueOf(fact.poiType())); pois.put(fact.id(), poi);
        }
        var goodsById = new HashMap<Long, Goods>();
        for (var fact : data.goods()) {
            var item = new Goods(fact.name(), fact.sku()); item.setId(fact.id());
            item.setWeightPerUnit(fact.weightPerUnitTonnes().doubleValue());
            item.setVolumePerUnit(fact.volumePerUnitCubicMeters().doubleValue());
            item.setVehicleFit(fact.vehicleFit()); goods.put(fact.sku(), item); goodsById.put(fact.id(), item);
        }
        for (var fact : data.vehicles()) {
            var vehicle = new Vehicle(); vehicle.setId(fact.id()); vehicle.setVehicleType(fact.vehicleType());
            vehicle.setMaxLoadCapacityTonnes(fact.maxLoadCapacityTonnes().doubleValue());
            vehicle.setCargoVolume(fact.cargoVolumeCubicMeters().doubleValue()); vehicles.add(vehicle);
        }
        for (var fact : data.processingChains()) {
            var chain = new ProcessingChain(); chain.setId(fact.id()); chain.setChainCode(fact.chainCode());
            chain.setChainName(fact.chainName()); chain.setStatus(ProcessingChain.ChainStatus.valueOf(fact.status()));
            var stages = new HashMap<Long, ProcessingStage>();
            var inputs = new HashMap<Long, ProcessingStageInput>();
            for (var f : fact.stages()) {
                var stage = new ProcessingStage(); stage.setId(f.id()); stage.setProcessingChain(chain);
                stage.setStageOrder(f.stageOrder()); stage.setStageKey(f.stageKey()); stage.setStageName(f.stageName());
                stage.setProcessingPOI(pois.get(f.processingPoiId()));
                stage.setInputGoods(goodsById.get(f.legacyInputGoodsId())); stage.setInputGoodsSku(f.legacyInputGoodsSku());
                stage.setInputWeightRatio(f.legacyInputWeightRatio().doubleValue());
                stage.setOutputGoods(goodsById.get(f.outputGoodsId())); stage.setOutputGoodsSku(f.outputGoodsSku());
                stage.setOutputWeightRatio(f.outputWeightRatio() == null ? null : f.outputWeightRatio().doubleValue());
                stage.setProcessingTimeMinutes(f.processingTimeMinutes());
                stage.setMinBatchSize(f.minBatchSize() == null ? null : f.minBatchSize().doubleValue());
                stage.setMaxCapacityPerCycle(f.maxCapacityPerCycle() == null ? null : f.maxCapacityPerCycle().doubleValue());
                var stageInputs = new ArrayList<ProcessingStageInput>();
                for (var input : f.inputs()) {
                    var row = new ProcessingStageInput(stage, input.inputKey(), input.sku(), input.inputShare().doubleValue());
                    row.setGoods(goodsById.get(input.goodsId()));
                    row.setId(input.id()); stageInputs.add(row); inputs.put(input.id(), row);
                }
                stageInputs.sort(Comparator.comparing(ProcessingStageInput::getInputKey));
                stage.setInputs(stageInputs); stages.put(f.id(), stage);
            }
            chain.setStages(new ArrayList<>(stages.values().stream()
                    .sorted(Comparator.comparing(ProcessingStage::getStageOrder)).toList()));
            var edges = new ArrayList<ProcessingStageEdge>();
            for (var f : fact.edges()) {
                var edge = new ProcessingStageEdge(chain, stages.get(f.fromStageId()), stages.get(f.toStageId()), inputs.get(f.toStageInputId()));
                edge.setId(f.id()); edges.add(edge);
            }
            // Match the production JPA @OrderBy, also used for execution-flow summation order.
            edges.sort(Comparator.comparing(ProcessingStageEdge::getId)); chain.setEdges(edges);
            ProcessingChainGraphValidator.validate(chain.getStages(), chain.getEdges()); chains.add(chain);
        }
        chains.sort(Comparator.comparing(ProcessingChain::getId));
    }
}
