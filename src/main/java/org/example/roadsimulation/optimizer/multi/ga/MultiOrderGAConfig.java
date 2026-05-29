package org.example.roadsimulation.optimizer.multi.ga;

public class MultiOrderGAConfig {

    private int populationSize = 20;
    private int maxGeneration = 30;
    private int noImproveLimit = 8;

    private double eliteRatio = 0.10;
    private double crossoverRate = 0.85;
    private double mutationRate = 0.25;

    private int tournamentSize = 5;

    public int getPopulationSize() {
        return populationSize;
    }

    public void setPopulationSize(int populationSize) {
        this.populationSize = populationSize;
    }

    public int getMaxGeneration() {
        return maxGeneration;
    }

    public void setMaxGeneration(int maxGeneration) {
        this.maxGeneration = maxGeneration;
    }

    public int getNoImproveLimit() {
        return noImproveLimit;
    }

    public void setNoImproveLimit(int noImproveLimit) {
        this.noImproveLimit = noImproveLimit;
    }

    public double getEliteRatio() {
        return eliteRatio;
    }

    public void setEliteRatio(double eliteRatio) {
        this.eliteRatio = eliteRatio;
    }

    public double getCrossoverRate() {
        return crossoverRate;
    }

    public void setCrossoverRate(double crossoverRate) {
        this.crossoverRate = crossoverRate;
    }

    public double getMutationRate() {
        return mutationRate;
    }

    public void setMutationRate(double mutationRate) {
        this.mutationRate = mutationRate;
    }

    public int getTournamentSize() {
        return tournamentSize;
    }

    public void setTournamentSize(int tournamentSize) {
        this.tournamentSize = tournamentSize;
    }
}