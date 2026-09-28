package org.example.roadsimulation.sandbox.baseline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingChain;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingEdge;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingInput;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingStage;

import java.util.Comparator;
import java.util.List;

/** Stable ordering and hashing for the data actually written to a workspace. */
public final class EffectiveBaseDataCodec {

    private final ObjectMapper objectMapper;
    private final LexicographicJsonSha256 jsonHash;

    public EffectiveBaseDataCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jsonHash = new LexicographicJsonSha256(objectMapper);
    }

    public Data normalize(Data source) {
        Comparator<ProcessingStage> stageOrder = Comparator.comparingInt(ProcessingStage::stageOrder)
                .thenComparingLong(ProcessingStage::id);
        List<ProcessingChain> chains = source.processingChains().stream()
                .map(chain -> new ProcessingChain(
                        chain.id(), chain.chainCode(), chain.chainName(), chain.status(), chain.description(),
                        chain.stages().stream()
                                .map(stage -> new ProcessingStage(
                                        stage.id(), stage.stageOrder(), stage.stageKey(), stage.stageName(),
                                        stage.description(), stage.processingPoiId(), stage.requiredPoiType(),
                                        stage.legacyInputGoodsId(), stage.legacyInputGoodsSku(),
                                        stage.legacyInputWeightRatio(), stage.outputGoodsId(),
                                        stage.outputGoodsSku(), stage.outputWeightRatio(),
                                        stage.processingTimeMinutes(), stage.minBatchSize(),
                                        stage.maxCapacityPerCycle(), stage.inputs().stream()
                                                .sorted(Comparator.comparing(ProcessingInput::inputKey)
                                                        .thenComparingLong(ProcessingInput::id))
                                                .toList()))
                                .sorted(stageOrder).toList(),
                        chain.edges().stream()
                                .sorted(Comparator.comparingLong(ProcessingEdge::fromStageId)
                                        .thenComparingLong(ProcessingEdge::toStageId)
                                        .thenComparingLong(ProcessingEdge::toStageInputId)
                                        .thenComparingLong(ProcessingEdge::id))
                                .toList()))
                .sorted(Comparator.comparingLong(ProcessingChain::id)).toList();
        return new Data(
                source.pois().stream().sorted(Comparator.comparingLong(SandboxBaselinePackageV1.Poi::id)).toList(),
                source.goods().stream().sorted(Comparator.comparingLong(SandboxBaselinePackageV1.Goods::id)).toList(),
                source.vehicles().stream().sorted(Comparator.comparingLong(SandboxBaselinePackageV1.Vehicle::id)).toList(),
                chains,
                source.initialInventories().stream()
                        .sorted(Comparator.comparingLong(SandboxBaselinePackageV1.InitialInventory::poiId)
                                .thenComparingLong(SandboxBaselinePackageV1.InitialInventory::goodsId))
                        .toList()
        );
    }

    public String hash(String selectionMode, String eligibilityPolicyVersion, Data data) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("selectionMode", selectionMode);
        payload.put("eligibilityPolicyVersion", eligibilityPolicyVersion);
        payload.set("data", objectMapper.valueToTree(normalize(data)));
        return jsonHash.hash(payload);
    }
}
