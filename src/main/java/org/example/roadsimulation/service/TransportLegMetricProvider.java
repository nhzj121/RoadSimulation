package org.example.roadsimulation.service;

import java.math.BigDecimal;

/** Optional execution-scoped metric source. Inactive means preserve the ordinary map implementation. */
public interface TransportLegMetricProvider {
    record Metric(double distanceMeters,long drivingSeconds) {}
    boolean active();
    default boolean metricsActive() { return active(); }
    Metric metric(Long fromPoi,BigDecimal fromLon,BigDecimal fromLat,Long toPoi,BigDecimal toLon,BigDecimal toLat);
}
