package org.example.roadsimulation.optimizer.multi.init;

/**
 * 多运单启发式算法初始化配置。
 */
public class InitialPopulationConfig {

    /**
     * 初始种群规模。
     */
    private int populationSize = 80;

    /**
     * 贪心精英个体数量。
     */
    private int greedyEliteCount = 10;

    /**
     * 基于贪心扰动生成的个体数量。
     */
    private int perturbedGreedyCount = 40;

    /**
     * 随机贪心个体数量。
     */
    private int randomizedGreedyCount = 30;

    /**
     * Top-K 随机插入。
     * 随机贪心时，不一定选最优插入位置，而是在前 K 个合法位置中随机选。
     */
    private int topKInsertionChoice = 5;

    /**
     * 扰动强度。
     * 例如 0.15 表示对 15% 的运单做顺序扰动。
     */
    private double perturbRatio = 0.15;

    public int getPopulationSize() {
        return populationSize;
    }

    public void setPopulationSize(int populationSize) {
        this.populationSize = populationSize;
    }

    public int getGreedyEliteCount() {
        return greedyEliteCount;
    }

    public void setGreedyEliteCount(int greedyEliteCount) {
        this.greedyEliteCount = greedyEliteCount;
    }

    public int getPerturbedGreedyCount() {
        return perturbedGreedyCount;
    }

    public void setPerturbedGreedyCount(int perturbedGreedyCount) {
        this.perturbedGreedyCount = perturbedGreedyCount;
    }

    public int getRandomizedGreedyCount() {
        return randomizedGreedyCount;
    }

    public void setRandomizedGreedyCount(int randomizedGreedyCount) {
        this.randomizedGreedyCount = randomizedGreedyCount;
    }

    public int getTopKInsertionChoice() {
        return topKInsertionChoice;
    }

    public void setTopKInsertionChoice(int topKInsertionChoice) {
        this.topKInsertionChoice = topKInsertionChoice;
    }

    public double getPerturbRatio() {
        return perturbRatio;
    }

    public void setPerturbRatio(double perturbRatio) {
        this.perturbRatio = perturbRatio;
    }
}