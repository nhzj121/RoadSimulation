package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Resolves the physical Goods record transported into a chain's terminal receiver. */
@Service
public class ProductionDemandCatalogService {

    private final ProcessingChainRepository chainRepository;
    private final GoodsRepository goodsRepository;

    public ProductionDemandCatalogService(
            ProcessingChainRepository chainRepository,
            GoodsRepository goodsRepository
    ) {
        this.chainRepository = chainRepository;
        this.goodsRepository = goodsRepository;
    }

    @Transactional(readOnly = true)
    public Goods resolveFinalTransportedGoods(Long chainId) {
        ProcessingChain chain = chainRepository.findById(chainId)
                .orElseThrow(() -> new IllegalArgumentException("加工链不存在: " + chainId));
        List<ProcessingStage> stages = chain.getStages().stream()
                .sorted(Comparator.comparing(ProcessingStage::getStageOrder))
                .toList();
        List<ProcessingChainGraphValidator.EdgeSpec> edges =
                ProcessingChainGraphValidator.normalizedEdges(stages, chain.getEdges());
        ProcessingChainGraphValidator.validate(stages, chain.getEdges());

        ProcessingStage sink = stages.stream()
                .filter(stage -> edges.stream().noneMatch(edge -> edge.fromStage() == stage))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("加工链缺少末端接收节点"));
        List<ProcessingChainGraphValidator.InputSpec> inputs =
                ProcessingChainGraphValidator.inputs(sink);
        if (inputs.size() != 1) {
            throw new IllegalStateException("末端接收节点必须且只能接收一种最终阶段货物");
        }
        String sku = inputs.get(0).sku();
        Goods goods = goodsRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalStateException("最终运输货物缺少 Goods 主数据: " + sku));
        if (goods.getWeightPerUnit() == null || goods.getWeightPerUnit() <= 0
                || !Double.isFinite(goods.getWeightPerUnit())) {
            throw new IllegalStateException("最终运输货物单位重量无效: " + sku);
        }
        return goods;
    }
}
