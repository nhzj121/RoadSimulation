package org.example.roadsimulation.optimizer;

import java.util.Arrays;

/**
 * 车辆-货物匹配方案编码
 *
 * assignment[i] = j  →  第i个ShipmentItem（列表下标）分配给第j辆Vehicle（列表下标）
 * assignment[i] = -1 →  未分配（产生高额惩罚）
 */
public class MatchingSolution {

    private final int[] assignment;
    private double totalCost = Double.MAX_VALUE;
    private boolean feasible = false;

    public MatchingSolution(int itemCount) {
        this.assignment = new int[itemCount];
        Arrays.fill(this.assignment, -1);
    }

    /** 深拷贝构造（GA种群繁殖 / SA邻域搜索时使用） */
    public MatchingSolution(MatchingSolution other) {
        this.assignment = Arrays.copyOf(other.assignment, other.assignment.length);
        this.totalCost  = other.totalCost;
        this.feasible   = other.feasible;
    }

    public int  getAssignment(int itemIdx)                 { return assignment[itemIdx]; }
    public void setAssignment(int itemIdx, int vehicleIdx) { assignment[itemIdx] = vehicleIdx; }
    public int  getItemCount()                             { return assignment.length; }
    public int[] getAssignmentArray()                      { return assignment; }

    public double  getTotalCost()              { return totalCost; }
    public void    setTotalCost(double c)      { this.totalCost = c; }
    public boolean isFeasible()                { return feasible; }
    public void    setFeasible(boolean f)      { this.feasible = f; }

    @Override
    public String toString() {
        return String.format("MatchingSolution{cost=%.2f, feasible=%s, items=%d}",
                totalCost, feasible, assignment.length);
    }
}
