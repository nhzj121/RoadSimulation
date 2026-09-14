package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.ProcessingStage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThat;

class ProcessingChainSkuValidatorTest {

    @Test
    void shouldAcceptConsistentStageSkus() {
        ProcessingStage first = stage(1, "RAW", "SEMI");
        ProcessingStage second = stage(2, "SEMI", "FINAL");

        assertThatCode(() -> ProcessingChainSkuValidator.validateStages(List.of(first, second)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAdjacentStageSkuMismatch() {
        ProcessingStage first = stage(1, "RAW", "SEMI_1");
        ProcessingStage second = stage(2, "SEMI_2", "FINAL");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProcessingChainSkuValidator.validateStages(List.of(first, second)))
                .withMessageContaining("工序 SKU 不一致")
                .withMessageContaining("SEMI_1")
                .withMessageContaining("SEMI_2");
    }

    @Test
    void shouldRejectGoodsAndSkuFieldMismatch() {
        ProcessingStage stage = stage(1, "RAW", "FINAL");
        stage.setOutputGoods(new Goods("Final", "FINAL_GOODS"));
        stage.setOutputGoodsSku("FINAL");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProcessingChainSkuValidator.validateStage(stage))
                .withMessageContaining("Goods 与 SKU 字段不一致");
    }

    @Test
    void shouldRejectMissingInputSku() {
        ProcessingStage stage = stage(1, null, "FINAL");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProcessingChainSkuValidator.validateStage(stage))
                .withMessageContaining("缺少输入 SKU");
    }

    @Test
    void shouldResolveSkuFromGoodsBeforeExplicitSkuField() {
        ProcessingStage stage = stage(1, "RAW", "FINAL");
        stage.setInputGoods(new Goods("Raw", "RAW_GOODS"));
        stage.setInputGoodsSku("RAW");

        assertThat(ProcessingChainSkuValidator.resolveInputSku(stage)).isEqualTo("RAW_GOODS");
    }

    private ProcessingStage stage(int order, String inputSku, String outputSku) {
        ProcessingStage stage = new ProcessingStage();
        stage.setStageOrder(order);
        stage.setStageName("Stage " + order);
        stage.setInputGoodsSku(inputSku);
        stage.setOutputGoodsSku(outputSku);
        stage.setProcessingTimeMinutes(60);
        return stage;
    }
}
