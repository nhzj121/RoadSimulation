package org.example.roadsimulation.entity;

public enum VehicleCategory {
    LIGHT_TRUCK("轻型货车", 0, 2000, 0, 10),
    MEDIUM_TRUCK("中型货车", 2000, 8000, 10, 30),
    HEAVY_TRUCK("重型货车", 8000, 20000, 30, 80),
    REFRIGERATED_TRUCK("冷藏车", 0, 15000, 0, 50),
    TANKER_TRUCK("罐车", 5000, 30000, 20, 60),
    FLATBED_TRUCK("平板车", 10000, 40000, 40, 100),
    CONTAINER_TRUCK("集装箱车", 10000, 35000, 30, 90);

    private final String displayName;
    private final double minWeight;
    private final double maxWeight;
    private final double minVolume;
    private final double maxVolume;

    VehicleCategory(String displayName, double minWeight, double maxWeight,
                    double minVolume, double maxVolume) {
        this.displayName = displayName;
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
        this.minVolume = minVolume;
        this.maxVolume = maxVolume;
    }

    public String getDisplayName() { return displayName; }
    public double getMinWeight() { return minWeight; }
    public double getMaxWeight() { return maxWeight; }
    public double getMinVolume() { return minVolume; }
    public double getMaxVolume() { return maxVolume; }
}