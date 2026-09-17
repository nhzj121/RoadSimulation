package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageEdge;
import org.example.roadsimulation.entity.ProcessingStageInput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates a processing chain as a directed acyclic graph and normalizes
 * legacy linear chains into the same graph representation.
 */
public final class ProcessingChainGraphValidator {

    public record InputSpec(
            ProcessingStage stage,
            String inputKey,
            String sku,
            Double inputShare,
            ProcessingStageInput persistentInput
    ) {}

    public record EdgeSpec(
            ProcessingStage fromStage,
            ProcessingStage toStage,
            InputSpec input
    ) {}

    private ProcessingChainGraphValidator() {}

    public static void validate(List<ProcessingStage> stages, List<ProcessingStageEdge> edges) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("加工链至少需要一道工序");
        }

        validateStages(stages);
        List<EdgeSpec> edgeSpecs = normalizedEdges(stages, edges);
        validateEdges(stages, edgeSpecs);
        List<ProcessingStage> order = topologicalOrder(stages, edgeSpecs);
        if (order.size() != stages.size()) {
            throw new IllegalArgumentException("加工链存在环或无效边");
        }

        Set<ProcessingStage> sinks = stages.stream()
                .filter(stage -> edgeSpecs.stream().noneMatch(edge -> edge.fromStage() == stage))
                .collect(Collectors.toSet());
        if (sinks.size() != 1) {
            throw new IllegalArgumentException("Y 型加工链必须且只能有一个最终工序");
        }

        for (ProcessingStage stage : stages) {
            boolean isSink = sinks.contains(stage);
            boolean hasOutgoing = edgeSpecs.stream().anyMatch(edge -> edge.fromStage() == stage);
            if (isSink && hasOutgoing) {
                throw new IllegalArgumentException("最终工序不能有下游工序");
            }
            if (!isSink && !hasOutgoing) {
                throw new IllegalArgumentException("非最终工序缺少下游工序: " + stage.getStageName());
            }
        }
    }

    public static List<InputSpec> inputs(ProcessingStage stage) {
        List<InputSpec> result = new ArrayList<>();
        if (stage.getInputs() != null && !stage.getInputs().isEmpty()) {
            for (ProcessingStageInput input : stage.getInputs()) {
                if (input == null) {
                    continue;
                }
                result.add(new InputSpec(
                        stage,
                        input.getInputKey(),
                        input.getSku(),
                        input.getInputShare(),
                        input
                ));
            }
        } else {
            result.add(new InputSpec(
                    stage,
                    "input",
                    ProcessingChainSkuValidator.resolveInputSku(stage),
                    1.0,
                    null
            ));
        }
        return result;
    }

    public static List<EdgeSpec> normalizedEdges(
            List<ProcessingStage> stages,
            List<ProcessingStageEdge> edges
    ) {
        if (edges == null || edges.isEmpty()) {
            List<ProcessingStage> ordered = stages.stream()
                    .sorted(Comparator.comparing(ProcessingStage::getStageOrder))
                    .toList();
            List<EdgeSpec> result = new ArrayList<>();
            for (int i = 0; i < ordered.size() - 1; i++) {
                ProcessingStage toStage = ordered.get(i + 1);
                List<InputSpec> inputs = inputs(toStage);
                result.add(new EdgeSpec(ordered.get(i), toStage, inputs.get(0)));
            }
            return result;
        }

        List<EdgeSpec> result = new ArrayList<>();
        for (ProcessingStageEdge edge : edges) {
            if (edge == null || edge.getFromStage() == null || edge.getToStage() == null
                    || edge.getToStageInput() == null) {
                throw new IllegalArgumentException("加工链边缺少工序或输入引用");
            }
            ProcessingStageInput persistentInput = edge.getToStageInput();
            if (persistentInput.getStage() != edge.getToStage()) {
                throw new IllegalArgumentException("加工链边引用了不属于目标工序的输入");
            }
            result.add(new EdgeSpec(
                    edge.getFromStage(),
                    edge.getToStage(),
                    new InputSpec(
                            edge.getToStage(),
                            persistentInput.getInputKey(),
                            persistentInput.getSku(),
                            persistentInput.getInputShare(),
                            persistentInput
                    )
            ));
        }
        return result;
    }

    public static List<ProcessingStage> topologicalOrder(
            List<ProcessingStage> stages,
            List<EdgeSpec> edges
    ) {
        Map<ProcessingStage, Integer> incoming = new IdentityHashMap<>();
        stages.forEach(stage -> incoming.put(stage, 0));
        for (EdgeSpec edge : edges) {
            incoming.merge(edge.toStage(), 1, Integer::sum);
        }

        List<ProcessingStage> ready = stages.stream()
                .filter(stage -> incoming.get(stage) == 0)
                .sorted(Comparator.comparing(ProcessingStage::getStageOrder))
                .collect(Collectors.toCollection(ArrayList::new));
        List<ProcessingStage> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            ProcessingStage current = ready.remove(0);
            result.add(current);
            for (EdgeSpec edge : edges) {
                if (edge.fromStage() != current) {
                    continue;
                }
                Integer degree = incoming.merge(edge.toStage(), -1, Integer::sum);
                if (degree == 0) {
                    ready.add(edge.toStage());
                }
            }
        }
        return result;
    }

    private static void validateStages(List<ProcessingStage> stages) {
        long distinctOrders = stages.stream()
                .map(ProcessingStage::getStageOrder)
                .distinct()
                .count();
        if (distinctOrders != stages.size()) {
            throw new IllegalArgumentException("工序顺序不能重复");
        }

        boolean usesStageKeys = stages.stream()
                .anyMatch(stage -> stage != null && stage.getStageKey() != null);
        Set<String> stageKeys = new HashSet<>();
        for (ProcessingStage stage : stages) {
            if (usesStageKeys) {
                if (stage.getStageKey() == null || stage.getStageKey().isBlank()) {
                    throw new IllegalArgumentException("图加工链工序标识不能为空: " + stage.getStageName());
                }
                if (!stageKeys.add(stage.getStageKey())) {
                    throw new IllegalArgumentException("图加工链工序标识不能重复: " + stage.getStageKey());
                }
            }
        }

        for (ProcessingStage stage : stages) {
            if (stage.getStageOrder() == null || stage.getStageOrder() <= 0) {
                throw new IllegalArgumentException("工序顺序必须大于 0");
            }
            if (stage.getStageName() == null || stage.getStageName().isBlank()) {
                throw new IllegalArgumentException("工序名称不能为空");
            }
            if (stage.getProcessingPOI() == null) {
                throw new IllegalArgumentException("工序缺少加工 POI: " + stage.getStageName());
            }
            if (stage.getProcessingTimeMinutes() == null || stage.getProcessingTimeMinutes() <= 0) {
                throw new IllegalArgumentException("工序加工时间必须大于 0: " + stage.getStageName());
            }
            double ratio = stage.getOutputWeightRatio() == null
                    ? 1.0
                    : stage.getOutputWeightRatio();
            if (ratio <= 0 || !Double.isFinite(ratio)) {
                throw new IllegalArgumentException("工序产出率必须大于 0: " + stage.getStageName());
            }
            ProcessingChainSkuValidator.resolveOutputSku(stage);

            List<InputSpec> inputs = inputs(stage);
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("工序缺少输入: " + stage.getStageName());
            }
            long distinctKeys = inputs.stream().map(InputSpec::inputKey).distinct().count();
            if (distinctKeys != inputs.size()) {
                throw new IllegalArgumentException("工序输入标识不能重复: " + stage.getStageName());
            }

            double shareSum = 0;
            for (InputSpec input : inputs) {
                if (input.inputKey() == null || input.inputKey().isBlank()) {
                    throw new IllegalArgumentException("工序输入标识不能为空: " + stage.getStageName());
                }
                if (input.sku() == null || input.sku().isBlank()) {
                    throw new IllegalArgumentException("工序输入 SKU 不能为空: " + stage.getStageName());
                }
                if (input.inputShare() == null || input.inputShare() <= 0
                        || !Double.isFinite(input.inputShare())) {
                    throw new IllegalArgumentException("工序输入占比必须大于 0: " + stage.getStageName());
                }
                shareSum += input.inputShare();
            }
            if (Math.abs(shareSum - 1.0) > 0.000001) {
                throw new IllegalArgumentException("工序输入占比之和必须等于 1: " + stage.getStageName());
            }
        }
    }

    private static void validateEdges(List<ProcessingStage> stages, List<EdgeSpec> edges) {
        Set<ProcessingStage> stageSet = Collections.newSetFromMap(new IdentityHashMap<>());
        stageSet.addAll(stages);
        Map<ProcessingStage, Set<String>> usedInputKeys = new IdentityHashMap<>();

        for (EdgeSpec edge : edges) {
            if (!stageSet.contains(edge.fromStage()) || !stageSet.contains(edge.toStage())) {
                throw new IllegalArgumentException("加工链边引用了不存在的工序");
            }
            if (edge.fromStage() == edge.toStage()) {
                throw new IllegalArgumentException("加工链边不能指向自身");
            }
            if (edge.input() == null || edge.toStage() != edge.input().stage()) {
                throw new IllegalArgumentException("加工链边引用了无效输入");
            }

            String outputSku = ProcessingChainSkuValidator.resolveOutputSku(edge.fromStage());
            if (!Objects.equals(outputSku, edge.input().sku())) {
                throw new IllegalArgumentException(
                        "加工链边 SKU 不一致: " + edge.fromStage().getStageName()
                                + " 输出 " + outputSku + "，"
                                + edge.toStage().getStageName() + " 输入 " + edge.input().sku()
                );
            }

            Set<String> inputKeys = usedInputKeys.computeIfAbsent(edge.toStage(), key -> new HashSet<>());
            if (!inputKeys.add(edge.input().inputKey())) {
                throw new IllegalArgumentException("工序输入只能有一个上游来源");
            }
        }

        for (ProcessingStage stage : stages) {
            long outCount = edges.stream().filter(edge -> edge.fromStage() == stage).count();
            if (outCount > 1) {
                throw new IllegalArgumentException("暂不支持一个工序输出分流: " + stage.getStageName());
            }
        }
    }
}
