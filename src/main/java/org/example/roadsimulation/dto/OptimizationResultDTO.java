package org.example.roadsimulation.dto;

import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.MatchingSolution;

import java.util.ArrayList;
import java.util.List;

/**
 * 优化结果 DTO
 * 包含：算法名、最优成本、是否满足硬约束、耗时、具体分配方案列表
 */
public class OptimizationResultDTO {

    private String  algorithmName;
    private double  totalCost;
    private boolean feasible;
    private long    elapsedMs;
    private int     vehiclesUsed;      // 实际启用的车辆数
    private int     itemsAssigned;     // 成功分配的货物明细数
    private int     itemsUnassigned;   // 未能分配的货物明细数

    /** 具体分配方案 */
    private List<AssignmentItem> assignments = new ArrayList<>();

    // ── 静态工厂 ────────────────────────────────────────────────────
    public static OptimizationResultDTO from(MatchingSolution solution,
                                             String algorithmName,
                                             long elapsedMs,
                                             List<Vehicle> vehicles,
                                             List<ShipmentItem> items) {
        OptimizationResultDTO dto = new OptimizationResultDTO();
        dto.algorithmName = algorithmName;
        dto.totalCost     = solution.getTotalCost();
        dto.feasible      = solution.isFeasible();
        dto.elapsedMs     = elapsedMs;

        int[] assignment = solution.getAssignmentArray();
        int assigned = 0, unassigned = 0;
        boolean[] vehicleUsed = new boolean[vehicles.size()];

        for (int i = 0; i < assignment.length; i++) {
            AssignmentItem ai = new AssignmentItem();
            ShipmentItem item = items.get(i);

            ai.shipmentItemId   = item.getId();
            ai.shipmentItemName = item.getName();
            ai.shipmentItemSku  = item.getSku();
            ai.weight           = item.getWeight();
            ai.volume           = item.getVolume();

            int vIdx = assignment[i];
            if (vIdx >= 0 && vIdx < vehicles.size()) {
                Vehicle v = vehicles.get(vIdx);
                ai.vehicleId           = v.getId();
                ai.vehicleLicensePlate = v.getLicensePlate();
                ai.vehicleType         = v.getVehicleType();
                ai.assigned            = true;
                vehicleUsed[vIdx]      = true;
                assigned++;
            } else {
                ai.vehicleId           = null;
                ai.vehicleLicensePlate = "UNASSIGNED";
                ai.assigned            = false;
                unassigned++;
            }
            dto.assignments.add(ai);
        }

        // 统计使用车辆数
        int usedCount = 0;
        for (boolean used : vehicleUsed) if (used) usedCount++;
        dto.vehiclesUsed    = usedCount;
        dto.itemsAssigned   = assigned;
        dto.itemsUnassigned = unassigned;

        return dto;
    }

    // ── 内部类：单条分配记录 ────────────────────────────────────────
    public static class AssignmentItem {
        public Long    shipmentItemId;
        public String  shipmentItemName;
        public String  shipmentItemSku;
        public Double  weight;
        public Double  volume;
        public Long    vehicleId;
        public String  vehicleLicensePlate;
        public String  vehicleType;
        public boolean assigned;
    }

    // ── Getters ──────────────────────────────────────────────────────
    public String              getAlgorithmName()   { return algorithmName;   }
    public double              getTotalCost()        { return totalCost;       }
    public boolean             isFeasible()          { return feasible;        }
    public long                getElapsedMs()        { return elapsedMs;       }
    public int                 getVehiclesUsed()     { return vehiclesUsed;    }
    public int                 getItemsAssigned()    { return itemsAssigned;   }
    public int                 getItemsUnassigned()  { return itemsUnassigned; }
    public List<AssignmentItem>getAssignments()      { return assignments;     }
}
