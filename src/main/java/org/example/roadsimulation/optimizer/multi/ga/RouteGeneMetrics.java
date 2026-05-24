package org.example.roadsimulation.optimizer.multi.ga;

public class RouteGeneMetrics {

    private final Long vehicleId;
    private final double score;
    private final int servedItemCount;
    private final double distanceKm;

    public RouteGeneMetrics(
            Long vehicleId,
            double score,
            int servedItemCount,
            double distanceKm
    ) {
        this.vehicleId = vehicleId;
        this.score = score;
        this.servedItemCount = servedItemCount;
        this.distanceKm = distanceKm;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public double getScore() {
        return score;
    }

    public int getServedItemCount() {
        return servedItemCount;
    }

    public double getDistanceKm() {
        return distanceKm;
    }
}