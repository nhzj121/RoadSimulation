package org.example.roadsimulation.optimizer.node;

import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.util.*;

/** Validates ordered node completeness, capacity progression and strict LIFO unloading. */
@Component
public class AssignmentNodeSequenceValidator {

    private static final double CAPACITY_TOLERANCE = 1e-6;

    public ValidationResult validate(List<AssignmentNode> nodes, Vehicle vehicle) {
        if (vehicle == null) {
            return ValidationResult.invalid("车辆为空");
        }

        if (nodes == null || nodes.isEmpty()) {
            return ValidationResult.valid();
        }

        Double maxLoad = vehicle.getMaxLoadCapacity();
        Double maxVolume = vehicle.getCargoVolume();

        if (maxLoad == null || maxLoad <= 0) {
            return ValidationResult.invalid("车辆最大载重无效");
        }

        if (maxVolume == null || maxVolume <= 0) {
            return ValidationResult.invalid("车辆最大容积无效");
        }

        List<AssignmentNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparing(
                AssignmentNode::getSequenceIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));

        Deque<Long> lifoStack = new ArrayDeque<>();
        Set<Long> loaded = new HashSet<>();
        Set<Long> unloaded = new HashSet<>();

        double currentWeight = 0.0;
        double currentVolume = 0.0;

        for (AssignmentNode node : ordered) {
            if (node.getActionType() == null) {
                return ValidationResult.invalid("节点动作类型为空");
            }

            currentWeight += safe(node.getWeightDelta());
            currentVolume += safe(node.getVolumeDelta());

            if (currentWeight < -CAPACITY_TOLERANCE) {
                return ValidationResult.invalid("节点序列导致车辆载重为负");
            }

            if (currentVolume < -CAPACITY_TOLERANCE) {
                return ValidationResult.invalid("节点序列导致车辆体积为负");
            }

            if (currentWeight > maxLoad + CAPACITY_TOLERANCE) {
                return ValidationResult.invalid("车辆超重");
            }

            if (currentVolume > maxVolume + CAPACITY_TOLERANCE) {
                return ValidationResult.invalid("车辆超体积");
            }

            switch (node.getActionType()) {
                case LOAD -> {
                    ShipmentItem item = node.getShipmentItem();
                    if (item == null || item.getId() == null) {
                        return ValidationResult.invalid("LOAD 节点缺少 ShipmentItem");
                    }

                    Long itemId = item.getId();

                    if (loaded.contains(itemId)) {
                        return ValidationResult.invalid("ShipmentItem 重复装货: " + itemId);
                    }

                    if (unloaded.contains(itemId)) {
                        return ValidationResult.invalid("ShipmentItem 已卸货后又装货: " + itemId);
                    }

                    loaded.add(itemId);
                    lifoStack.push(itemId);
                }

                case UNLOAD -> {
                    ShipmentItem item = node.getShipmentItem();
                    if (item == null || item.getId() == null) {
                        return ValidationResult.invalid("UNLOAD 节点缺少 ShipmentItem");
                    }

                    Long itemId = item.getId();

                    if (!loaded.contains(itemId)) {
                        return ValidationResult.invalid("未装先卸: " + itemId);
                    }

                    if (unloaded.contains(itemId)) {
                        return ValidationResult.invalid("ShipmentItem 重复卸货: " + itemId);
                    }

                    if (lifoStack.isEmpty()) {
                        return ValidationResult.invalid("LIFO 栈为空但发生卸货");
                    }

                    Long top = lifoStack.peek();
                    if (!Objects.equals(top, itemId)) {
                        return ValidationResult.invalid(
                                "违反 LIFO，当前栈顶为 " + top + "，但尝试卸货 " + itemId
                        );
                    }

                    lifoStack.pop();
                    unloaded.add(itemId);
                }

                case PASS_BY -> {
                    // 暂不处理
                }
            }
        }

        if (!lifoStack.isEmpty()) {
            return ValidationResult.invalid("存在已装未卸货物");
        }

        if (!loaded.equals(unloaded)) {
            return ValidationResult.invalid("装卸集合不一致");
        }

        return ValidationResult.valid();
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String reason;

        private ValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }

        public boolean isValid() {
            return valid;
        }

        public String getReason() {
            return reason;
        }
    }
}
