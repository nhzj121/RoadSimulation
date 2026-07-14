package org.example.roadsimulation.optimizer.multi.insertion;

import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.node.AssignmentNodeFactory;
import org.example.roadsimulation.optimizer.node.AssignmentNodeSequenceValidator;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 合法插入算子。
 *
 * 作用：
 * 给定某辆车当前已有的 AssignmentNode 序列，尝试把一个新的 ShipmentItem
 * 以 LOAD + UNLOAD 两个节点插入进去，并返回所有合法插入候选。
 *
 * 合法性要求：
 * 1. LOAD 必须早于 UNLOAD
 * 2. 任意时刻不超重
 * 3. 任意时刻不超体积
 * 4. 满足严格 LIFO
 * 5. 同一个 ShipmentItem 不允许重复出现在同一路线中
 */
@Service
public class FeasibleInsertionService {

    private final AssignmentNodeFactory nodeFactory;
    private final AssignmentNodeSequenceValidator validator;
    private final RouteSequenceCostEstimator costEstimator;

    public FeasibleInsertionService(
            AssignmentNodeFactory nodeFactory,
            AssignmentNodeSequenceValidator validator,
            RouteSequenceCostEstimator costEstimator
    ) {
        this.nodeFactory = nodeFactory;
        this.validator = validator;
        this.costEstimator = costEstimator;
    }

    /**
     * 查找最佳合法插入方案。
     *
     * @return Optional.empty() 表示该 item 无法插入该车辆当前路线。
     */
    public Optional<InsertionCandidate> findBestInsertion(
            Vehicle vehicle,
            List<AssignmentNode> currentNodes,
            ShipmentItem item
    ) {
        List<InsertionCandidate> candidates = findAllFeasibleInsertions(vehicle, currentNodes, item);

        return candidates.stream()
                .min(Comparator.comparingDouble(InsertionCandidate::getScore));
    }

    /**
     * 枚举所有合法插入方案。
     */
    public List<InsertionCandidate> findAllFeasibleInsertions(
            Vehicle vehicle,
            List<AssignmentNode> currentNodes,
            ShipmentItem item
    ) {
        validateInput(vehicle, item);

        List<AssignmentNode> base = normalizeAndCopy(currentNodes);

        if (alreadyContainsItem(base, item)) {
            return Collections.emptyList();
        }

        List<InsertionCandidate> result = new ArrayList<>();

        double beforeDistance = costEstimator.estimateTotalDistanceKm(vehicle, base);

        int n = base.size();

        /*
         * loadIndex 取值范围：0..n
         * 表示 LOAD 可以插入到当前序列的任意位置，包括最前和最后。
         */
        for (int loadIndex = 0; loadIndex <= n; loadIndex++) {

            List<AssignmentNode> afterLoad = normalizeAndCopy(base);
            AssignmentNode loadNode = nodeFactory.createLoadNode(null, loadIndex, item);
            afterLoad.add(loadIndex, loadNode);
            normalizeSequence(afterLoad);

            /*
             * unloadIndex 取值范围：loadIndex + 1 .. afterLoad.size()
             * 注意这里是在 LOAD 已经插入后的序列中插入 UNLOAD。
             */
            for (int unloadIndex = loadIndex + 1; unloadIndex <= afterLoad.size(); unloadIndex++) {
                List<AssignmentNode> candidateNodes = normalizeAndCopy(afterLoad);

                AssignmentNode unloadNode = nodeFactory.createUnloadNode(null, unloadIndex, item);
                candidateNodes.add(unloadIndex, unloadNode);
                normalizeSequence(candidateNodes);

                AssignmentNodeSequenceValidator.ValidationResult validation =
                        validator.validate(candidateNodes, vehicle);

                if (!validation.isValid()) {
                    continue;
                }

                double afterDistance = costEstimator.estimateTotalDistanceKm(vehicle, candidateNodes);
                double score = costEstimator.estimateInsertionScore(vehicle, item, base, candidateNodes);

                result.add(new InsertionCandidate(
                        vehicle,
                        item,
                        loadIndex,
                        unloadIndex,
                        candidateNodes,
                        beforeDistance,
                        afterDistance,
                        score
                ));
            }
        }

        return result;
    }

    /**
     * 将 item 按指定位置插入。
     * 用于交叉/变异修复阶段在已经选定候选后直接拿结果。
     */
    public List<AssignmentNode> applyInsertion(InsertionCandidate candidate) {
        if (candidate == null) {
            return Collections.emptyList();
        }
        return candidate.getNodesAfterInsertion();
    }

    private void validateInput(Vehicle vehicle, ShipmentItem item) {
        if (vehicle == null) {
            throw new IllegalArgumentException("vehicle 不能为空");
        }

        if (item == null) {
            throw new IllegalArgumentException("shipmentItem 不能为空");
        }

        if (item.getId() == null) {
            throw new IllegalArgumentException("shipmentItem.id 不能为空");
        }

        if (item.getShipment() == null) {
            throw new IllegalArgumentException("shipmentItem 缺少 shipment");
        }

        if (item.getWeight() == null || item.getWeight() < 0) {
            throw new IllegalArgumentException("shipmentItem.weight 非法");
        }

        if (item.getVolume() == null || item.getVolume() < 0) {
            throw new IllegalArgumentException("shipmentItem.volume 非法");
        }
    }

    private boolean alreadyContainsItem(List<AssignmentNode> nodes, ShipmentItem item) {
        if (nodes == null || nodes.isEmpty() || item == null || item.getId() == null) {
            return false;
        }

        for (AssignmentNode node : nodes) {
            if (node.getShipmentItem() == null || node.getShipmentItem().getId() == null) {
                continue;
            }

            if (Objects.equals(node.getShipmentItem().getId(), item.getId())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 排序 + 深拷贝，避免在枚举插入时污染 JPA 托管实体。
     */
    private List<AssignmentNode> normalizeAndCopy(List<AssignmentNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }

        List<AssignmentNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparing(
                AssignmentNode::getSequenceIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));

        List<AssignmentNode> copied = new ArrayList<>();
        for (AssignmentNode node : ordered) {
            copied.add(copyNodeForPlanning(node));
        }

        normalizeSequence(copied);
        return copied;
    }

    /**
     * 注意：这里创建的是用于算法搜索的临时节点，不应该直接复用数据库中的 AssignmentNode。
     */
    private AssignmentNode copyNodeForPlanning(AssignmentNode source) {
        AssignmentNode target = new AssignmentNode();

        target.setAssignment(source.getAssignment());
        target.setSequenceIndex(source.getSequenceIndex());
        target.setPoi(source.getPoi());
        target.setActionType(source.getActionType());
        target.setShipmentItem(source.getShipmentItem());
        target.setWeightDelta(source.getWeightDelta());
        target.setVolumeDelta(source.getVolumeDelta());
        target.setCompleted(false);
        target.setActualArrivalTime(null);

        return target;
    }

    private void normalizeSequence(List<AssignmentNode> nodes) {
        if (nodes == null) {
            return;
        }

        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).setSequenceIndex(i);
        }
    }
}
