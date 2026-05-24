package org.example.roadsimulation.optimizer.multi.cost;

/**
 * 单车路线指标。
 */
public class RouteMetrics {

    private double totalDistanceKm;
    private double emptyDistanceKm;

    private double maxLoadTon;
    private double maxVolumeM3;

    private double loadedTonKm;
    private double theoreticalTonKm;
    private double capacityWasteTonKm;

    private double maxWeightOnRoute;
    private double maxVolumeOnRoute;

    private double finalWeight;
    private double finalVolume;

    private double weightUtilization;
    private double volumeUtilization;
    private double effectiveUtilization;

    private int servedItemCount;

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public void setTotalDistanceKm(double totalDistanceKm) {
        this.totalDistanceKm = totalDistanceKm;
    }

    public double getEmptyDistanceKm() {
        return emptyDistanceKm;
    }

    public void setEmptyDistanceKm(double emptyDistanceKm) {
        this.emptyDistanceKm = emptyDistanceKm;
    }

    public double getMaxLoadTon() {
        return maxLoadTon;
    }

    public void setMaxLoadTon(double maxLoadTon) {
        this.maxLoadTon = maxLoadTon;
    }

    public double getMaxVolumeM3() {
        return maxVolumeM3;
    }

    public void setMaxVolumeM3(double maxVolumeM3) {
        this.maxVolumeM3 = maxVolumeM3;
    }

    public double getLoadedTonKm() {
        return loadedTonKm;
    }

    public void setLoadedTonKm(double loadedTonKm) {
        this.loadedTonKm = loadedTonKm;
    }

    public double getTheoreticalTonKm() {
        return theoreticalTonKm;
    }

    public void setTheoreticalTonKm(double theoreticalTonKm) {
        this.theoreticalTonKm = theoreticalTonKm;
    }

    public double getCapacityWasteTonKm() {
        return capacityWasteTonKm;
    }

    public void setCapacityWasteTonKm(double capacityWasteTonKm) {
        this.capacityWasteTonKm = capacityWasteTonKm;
    }

    public double getMaxWeightOnRoute() {
        return maxWeightOnRoute;
    }

    public void setMaxWeightOnRoute(double maxWeightOnRoute) {
        this.maxWeightOnRoute = maxWeightOnRoute;
    }

    public double getMaxVolumeOnRoute() {
        return maxVolumeOnRoute;
    }

    public void setMaxVolumeOnRoute(double maxVolumeOnRoute) {
        this.maxVolumeOnRoute = maxVolumeOnRoute;
    }

    public double getFinalWeight() {
        return finalWeight;
    }

    public void setFinalWeight(double finalWeight) {
        this.finalWeight = finalWeight;
    }

    public double getFinalVolume() {
        return finalVolume;
    }

    public void setFinalVolume(double finalVolume) {
        this.finalVolume = finalVolume;
    }

    public double getWeightUtilization() {
        return weightUtilization;
    }

    public void setWeightUtilization(double weightUtilization) {
        this.weightUtilization = weightUtilization;
    }

    public double getVolumeUtilization() {
        return volumeUtilization;
    }

    public void setVolumeUtilization(double volumeUtilization) {
        this.volumeUtilization = volumeUtilization;
    }

    public double getEffectiveUtilization() {
        return effectiveUtilization;
    }

    public void setEffectiveUtilization(double effectiveUtilization) {
        this.effectiveUtilization = effectiveUtilization;
    }

    public int getServedItemCount() {
        return servedItemCount;
    }

    public void setServedItemCount(int servedItemCount) {
        this.servedItemCount = servedItemCount;
    }
}