package org.example.roadsimulation.dto;

public class RuntimeCostDTO {
    private Double costA;
    private Double costB;
    private Double costC;
    private Double costD;
    private Double costE;
    private Double allCost;
    private Double normalizedCostA;
    private Double normalizedCostB;
    private Double normalizedCostC;
    private Double normalizedCostD;
    private Double normalizedCostE;
    private Double normalizedAllCost;
    private String baselinePercentile;
    private String baselineStrategy;

    public RuntimeCostDTO() {
    }

    public RuntimeCostDTO(Double costA, Double costB, Double costC, Double costD, Double costE, Double allCost) {
        this.costA = costA;
        this.costB = costB;
        this.costC = costC;
        this.costD = costD;
        this.costE = costE;
        this.allCost = allCost;
    }

    public Double getCostA() {
        return costA;
    }

    public void setCostA(Double costA) {
        this.costA = costA;
    }

    public Double getCostB() {
        return costB;
    }

    public void setCostB(Double costB) {
        this.costB = costB;
    }

    public Double getCostC() {
        return costC;
    }

    public void setCostC(Double costC) {
        this.costC = costC;
    }

    public Double getCostD() {
        return costD;
    }

    public void setCostD(Double costD) {
        this.costD = costD;
    }

    public Double getCostE() {
        return costE;
    }

    public void setCostE(Double costE) {
        this.costE = costE;
    }

    public Double getAllCost() {
        return allCost;
    }

    public void setAllCost(Double allCost) {
        this.allCost = allCost;
    }

    public Double getNormalizedCostA() {
        return normalizedCostA;
    }

    public void setNormalizedCostA(Double normalizedCostA) {
        this.normalizedCostA = normalizedCostA;
    }

    public Double getNormalizedCostB() {
        return normalizedCostB;
    }

    public void setNormalizedCostB(Double normalizedCostB) {
        this.normalizedCostB = normalizedCostB;
    }

    public Double getNormalizedCostC() {
        return normalizedCostC;
    }

    public void setNormalizedCostC(Double normalizedCostC) {
        this.normalizedCostC = normalizedCostC;
    }

    public Double getNormalizedCostD() {
        return normalizedCostD;
    }

    public void setNormalizedCostD(Double normalizedCostD) {
        this.normalizedCostD = normalizedCostD;
    }

    public Double getNormalizedCostE() {
        return normalizedCostE;
    }

    public void setNormalizedCostE(Double normalizedCostE) {
        this.normalizedCostE = normalizedCostE;
    }

    public Double getNormalizedAllCost() {
        return normalizedAllCost;
    }

    public void setNormalizedAllCost(Double normalizedAllCost) {
        this.normalizedAllCost = normalizedAllCost;
    }

    public String getBaselinePercentile() {
        return baselinePercentile;
    }

    public void setBaselinePercentile(String baselinePercentile) {
        this.baselinePercentile = baselinePercentile;
    }

    public String getBaselineStrategy() {
        return baselineStrategy;
    }

    public void setBaselineStrategy(String baselineStrategy) {
        this.baselineStrategy = baselineStrategy;
    }
}
