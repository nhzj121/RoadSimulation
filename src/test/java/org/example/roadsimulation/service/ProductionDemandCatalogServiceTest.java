package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionDemandCatalogServiceTest {

    @Test
    void resolvesGoodsTransportedIntoSinkInsteadOfMissingSinkOutputGoods() {
        ProcessingChainRepository chainRepository = mock(ProcessingChainRepository.class);
        GoodsRepository goodsRepository = mock(GoodsRepository.class);
        ProductionDemandCatalogService service =
                new ProductionDemandCatalogService(chainRepository, goodsRepository);

        ProcessingChain chain = new ProcessingChain();
        chain.setId(1L);
        chain.setStatus(ProcessingChain.ChainStatus.ACTIVE);
        ProcessingStage source = stage(1, "RAW", "RAW");
        ProcessingStage processing = stage(2, "RAW", "PANEL");
        ProcessingStage sink = stage(3, "PANEL", "FURNITURE");
        List.of(source, processing, sink).forEach(stage -> stage.setProcessingChain(chain));
        chain.setStages(new ArrayList<>(List.of(source, processing, sink)));

        Goods panel = new Goods("Panel", "PANEL");
        panel.setWeightPerUnit(0.1);
        when(chainRepository.findById(1L)).thenReturn(Optional.of(chain));
        when(goodsRepository.findBySku("PANEL")).thenReturn(Optional.of(panel));

        assertThat(service.resolveFinalTransportedGoods(1L)).isSameAs(panel);
    }

    private ProcessingStage stage(int order, String inputSku, String outputSku) {
        ProcessingStage stage = new ProcessingStage();
        stage.setId((long) order);
        stage.setStageOrder(order);
        stage.setStageName("Stage " + order);
        stage.setInputGoodsSku(inputSku);
        stage.setOutputGoodsSku(outputSku);
        stage.setOutputWeightRatio(1.0);
        stage.setProcessingTimeMinutes(60);
        stage.setProcessingPOI(new POI(
                "POI " + order,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                POI.POIType.WAREHOUSE
        ));
        return stage;
    }
}
