package org.example.roadsimulation.optimizer.multi.ga;

/**
 * 多运单 GA 变异参数。
 */
public class MutationConfig {

    /**
     * 单运单重插概率。
     */
    private double singleReinsertProbability = 0.45;

    /**
     * 多运单 destroy-repair 概率。
     */
    private double destroyRepairProbability = 0.35;

    /**
     * 车辆路线扰动概率。
     * 即随机选择一辆车，移除其部分运单再重插。
     */
    private double routeShakeProbability = 0.20;

    /**
     * destroy-repair 中最多移除多少个运单。
     */
    private int maxDestroyCount = 5;

    /**
     * route-shake 中最多移除一辆车上的多少个运单。
     */
    private int maxRouteShakeCount = 4;

    /**
     * 重插时 Top-K 随机选择范围。
     */
    private int topKInsertionChoice = 3;

    /**
     * 重插时选择最优位置的概率。
     * 其余概率从 Top-K 中随机选择。
     */
    private double bestInsertionProbability = 0.85;

    public double getSingleReinsertProbability() {
        return singleReinsertProbability;
    }

    public void setSingleReinsertProbability(double singleReinsertProbability) {
        this.singleReinsertProbability = singleReinsertProbability;
    }

    public double getDestroyRepairProbability() {
        return destroyRepairProbability;
    }

    public void setDestroyRepairProbability(double destroyRepairProbability) {
        this.destroyRepairProbability = destroyRepairProbability;
    }

    public double getRouteShakeProbability() {
        return routeShakeProbability;
    }

    public void setRouteShakeProbability(double routeShakeProbability) {
        this.routeShakeProbability = routeShakeProbability;
    }

    public int getMaxDestroyCount() {
        return maxDestroyCount;
    }

    public void setMaxDestroyCount(int maxDestroyCount) {
        this.maxDestroyCount = maxDestroyCount;
    }

    public int getMaxRouteShakeCount() {
        return maxRouteShakeCount;
    }

    public void setMaxRouteShakeCount(int maxRouteShakeCount) {
        this.maxRouteShakeCount = maxRouteShakeCount;
    }

    public int getTopKInsertionChoice() {
        return topKInsertionChoice;
    }

    public void setTopKInsertionChoice(int topKInsertionChoice) {
        this.topKInsertionChoice = topKInsertionChoice;
    }

    public double getBestInsertionProbability() {
        return bestInsertionProbability;
    }

    public void setBestInsertionProbability(double bestInsertionProbability) {
        this.bestInsertionProbability = bestInsertionProbability;
    }
}