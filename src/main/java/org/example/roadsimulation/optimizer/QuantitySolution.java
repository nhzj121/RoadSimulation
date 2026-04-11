package org.example.roadsimulation.optimizer;

import java.util.Arrays;

/**
 * 货物数量分配方案编码
 *
 * quantities[j] = k  →  第j辆车分配k件货物
 * quantities[j] = 0  →  第j辆车不参与本次运输
 *
 * 这里优化的是"如何把 totalQuantity 件货物分配给候选车辆"，
 * 目标：在满足车辆载重约束、货物适配约束的前提下，
 *       最小化派车成本（减少参与车辆数）+ 最大化载重利用率
 */
public class QuantitySolution {

    /** 每辆车分配的货物数量，下标与 vehicles 列表对应 */
    private final int[] quantities;

    /** 该方案的评估成本（越低越好） */
    private double cost = Double.MAX_VALUE;

    /** 是否满足所有硬约束（超重、不适配等） */
    private boolean feasible = false;

    public QuantitySolution(int vehicleCount) {
        this.quantities = new int[vehicleCount];
    }

    /** 深拷贝构造（GA/SA迭代时使用） */
    public QuantitySolution(QuantitySolution other) {
        this.quantities = Arrays.copyOf(other.quantities, other.quantities.length);
        this.cost      = other.cost;
        this.feasible  = other.feasible;
    }

    public int  getQuantity(int vehicleIdx)              { return quantities[vehicleIdx]; }
    public void setQuantity(int vehicleIdx, int qty)     { quantities[vehicleIdx] = qty; }
    public int  getVehicleCount()                        { return quantities.length; }
    public int[] getQuantities()                         { return quantities; }

    /** 计算总分配数量 */
    public int totalAssigned() {
        int sum = 0;
        for (int q : quantities) sum += q;
        return sum;
    }

    /** 参与运输的车辆数（数量>0的车辆） */
    public int usedVehicleCount() {
        int count = 0;
        for (int q : quantities) if (q > 0) count++;
        return count;
    }

    public double  getCost()              { return cost; }
    public void    setCost(double cost)   { this.cost = cost; }
    public boolean isFeasible()           { return feasible; }
    public void    setFeasible(boolean f) { this.feasible = f; }

    @Override
    public String toString() {
        return String.format("QuantitySolution{cost=%.2f, feasible=%s, used=%d辆, quantities=%s}",
                cost, feasible, usedVehicleCount(), Arrays.toString(quantities));
    }
}
