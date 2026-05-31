package org.example.roadsimulation.dto;

public class RuntimeCostDTO {
    private Double costA;
    private Double costB;
    private Double costC;
    private Double costD;
    private Double costE;
    private Double allCost;

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
}
