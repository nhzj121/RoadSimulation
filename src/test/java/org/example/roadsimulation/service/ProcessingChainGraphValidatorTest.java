package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageEdge;
import org.example.roadsimulation.entity.ProcessingStageInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProcessingChainGraphValidatorTest {

    @Test
    void validYShapeGraphPasses() {
        ProcessingChain chain = yChain();
        assertThatCode(() -> ProcessingChainGraphValidator.validate(chain.getStages(), chain.getEdges()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCycle() {
        ProcessingChain chain = yChain();
        ProcessingStage steel = chain.getStages().get(0);
        ProcessingStageInput upstream = new ProcessingStageInput(steel, "recycled", "FINAL", 1.0);
        steel.getInputs().add(upstream);
        chain.getEdges().add(new ProcessingStageEdge(
                chain, chain.getStages().get(2), steel, upstream
        ));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProcessingChainGraphValidator.validate(chain.getStages(), chain.getEdges()))
                .withMessageContaining("环");
    }

    @Test
    void rejectsInvalidInputShareSum() {
        ProcessingChain chain = yChain();
        ProcessingStageInput steelInput = chain.getStages().get(2).getInputs().get(0);
        steelInput.setInputShare(0.7);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProcessingChainGraphValidator.validate(chain.getStages(), chain.getEdges()))
                .withMessageContaining("输入占比之和必须等于 1");
    }

    @Test
    void rejectsEdgeSkuMismatch() {
        ProcessingChain chain = yChain();
        ProcessingStageInput steelInput = chain.getStages().get(2).getInputs().get(0);
        steelInput.setSku("WRONG");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProcessingChainGraphValidator.validate(chain.getStages(), chain.getEdges()))
                .withMessageContaining("SKU 不一致");
    }

    @Test
    void rejectsMultipleSinks() {
        ProcessingChain chain = yChain();
        ProcessingStage extra = stage(4L, 4, "extra", "RAW", "OTHER", 1.0);
        extra.setStageKey("extra");
        extra.setProcessingChain(chain);
        chain.getStages().add(extra);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProcessingChainGraphValidator.validate(chain.getStages(), chain.getEdges()))
                .withMessageContaining("只能有一个最终工序");
    }

    @Test
    void rejectsOutputSplit() {
        ProcessingChain chain = yChain();
        ProcessingStage extra = stage(4L, 4, "extra", "STEEL", "OTHER", 1.0);
        extra.setStageKey("extra");
        extra.setProcessingChain(chain);
        chain.getStages().add(extra);
        ProcessingStageInput extraInput = new ProcessingStageInput(extra, "input", "STEEL", 1.0);
        extra.getInputs().add(extraInput);
        chain.getEdges().add(new ProcessingStageEdge(
                chain, chain.getStages().get(0), extra, extraInput
        ));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProcessingChainGraphValidator.validate(chain.getStages(), chain.getEdges()))
                .withMessageContaining("暂不支持一个工序输出分流");
    }

    @Test
    void rejectsDuplicateStageKey() {
        ProcessingChain chain = yChain();
        chain.getStages().get(0).setStageKey("wood");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProcessingChainGraphValidator.validate(chain.getStages(), chain.getEdges()))
                .withMessageContaining("工序标识不能重复");
    }

    private ProcessingChain yChain() {
        ProcessingChain chain = new ProcessingChain();
        chain.setId(1L);
        chain.setChainCode("Y");
        chain.setChainName("Y chain");

        ProcessingStage steel = stage(1L, 1, "steel", "ORE", "STEEL", 1.0);
        ProcessingStage wood = stage(2L, 2, "wood", "TIMBER", "WOOD", 1.0);
        ProcessingStage merge = stage(3L, 3, "merge", null, "FINAL", 0.95);
        steel.setStageKey("steel");
        wood.setStageKey("wood");
        merge.setStageKey("merge");

        ProcessingStageInput steelInput = new ProcessingStageInput(merge, "steel", "STEEL", 0.8);
        ProcessingStageInput woodInput = new ProcessingStageInput(merge, "wood", "WOOD", 0.2);
        merge.setInputs(new ArrayList<>(List.of(steelInput, woodInput)));

        List.of(steel, wood, merge).forEach(stage -> stage.setProcessingChain(chain));
        chain.setStages(new ArrayList<>(List.of(steel, wood, merge)));
        chain.setEdges(new ArrayList<>(List.of(
                new ProcessingStageEdge(chain, steel, merge, steelInput),
                new ProcessingStageEdge(chain, wood, merge, woodInput)
        )));
        return chain;
    }

    private ProcessingStage stage(
            Long id,
            int order,
            String name,
            String inputSku,
            String outputSku,
            double outputRatio
    ) {
        ProcessingStage stage = new ProcessingStage();
        stage.setId(id);
        stage.setStageOrder(order);
        stage.setStageName(name);
        stage.setProcessingPOI(new POI(name, BigDecimal.ZERO, BigDecimal.ZERO, POI.POIType.WAREHOUSE));
        stage.setInputGoodsSku(inputSku);
        stage.setOutputGoodsSku(outputSku);
        stage.setOutputWeightRatio(outputRatio);
        stage.setProcessingTimeMinutes(60);
        stage.setInputs(new ArrayList<>());
        return stage;
    }
}
