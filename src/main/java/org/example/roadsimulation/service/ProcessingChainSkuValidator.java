package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.ProcessingStage;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Validates that the SKUs flowing through a processing chain are internally consistent.
 */
public final class ProcessingChainSkuValidator {

    private ProcessingChainSkuValidator() {
    }

    public static void validateStages(List<ProcessingStage> stages) {
        if (stages == null || stages.isEmpty()) {
            return;
        }

        if (stages.stream().anyMatch(stage -> stage != null && stage.getStageOrder() == null)) {
            throw new IllegalArgumentException("工序顺序不能为空");
        }

        List<ProcessingStage> orderedStages = stages.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ProcessingStage::getStageOrder))
                .toList();

        orderedStages.forEach(ProcessingChainSkuValidator::validateStage);

        for (int i = 0; i < orderedStages.size() - 1; i++) {
            ProcessingStage current = orderedStages.get(i);
            ProcessingStage next = orderedStages.get(i + 1);
            String currentOutput = resolveOutputSku(current);
            String nextInput = resolveInputSku(next);

            if (!Objects.equals(currentOutput, nextInput)) {
                throw new IllegalArgumentException(
                        "工序 SKU 不一致: 第 " + current.getStageOrder() + " 道工序输出 SKU 为 "
                                + currentOutput + "，第 " + next.getStageOrder() + " 道工序输入 SKU 为 "
                                + nextInput
                );
            }
        }
    }

    public static void validateStage(ProcessingStage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("工序不能为空");
        }
        validateSingleSku(stage, true);
        validateSingleSku(stage, false);
    }

    public static String resolveInputSku(ProcessingStage stage) {
        if (stage == null) {
            return null;
        }
        return resolveSku(stage.getInputGoods(), stage.getInputGoodsSku(), "输入");
    }

    public static String resolveOutputSku(ProcessingStage stage) {
        if (stage == null) {
            return null;
        }
        return resolveSku(stage.getOutputGoods(), stage.getOutputGoodsSku(), "输出");
    }

    private static void validateSingleSku(ProcessingStage stage, boolean input) {
        Goods goods = input ? stage.getInputGoods() : stage.getOutputGoods();
        String explicitSku = input ? stage.getInputGoodsSku() : stage.getOutputGoodsSku();
        String goodsSku = goods == null ? null : normalize(goods.getSku());
        String sku = normalize(explicitSku);

        if (!hasText(goodsSku) && !hasText(sku)) {
            throw new IllegalArgumentException(
                    "工序缺少" + (input ? "输入" : "输出") + " SKU: " + stage.getStageName()
            );
        }
        if (hasText(goodsSku) && hasText(sku) && !Objects.equals(goodsSku, sku)) {
            throw new IllegalArgumentException(
                    "工序" + (input ? "输入" : "输出") + " Goods 与 SKU 字段不一致: "
                            + stage.getStageName() + "，goodsSku=" + goodsSku + "，sku=" + sku
            );
        }
    }

    private static String resolveSku(Goods goods, String explicitSku, String direction) {
        String goodsSku = goods == null ? null : normalize(goods.getSku());
        String sku = normalize(explicitSku);
        if (hasText(goodsSku)) {
            return goodsSku;
        }
        if (hasText(sku)) {
            return sku;
        }
        throw new IllegalArgumentException("工序缺少" + direction + " SKU");
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
