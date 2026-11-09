package org.example.roadsimulation.sandbox.optimization;

/** Original plan owner, unaffected by physical replacement vehicles. */
public record TripKey(long logicalVehicleId, int tripIndex) implements Comparable<TripKey> {
    public TripKey {
        if (logicalVehicleId <= 0 || tripIndex < 0) throw new IllegalArgumentException("Invalid trip key");
    }
    public int compareTo(TripKey other) {
        int owner = Long.compare(logicalVehicleId, other.logicalVehicleId);
        return owner == 0 ? Integer.compare(tripIndex, other.tripIndex) : owner;
    }
}
