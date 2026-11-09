package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.*;

/** Frozen approximation, deliberately independent of road/map DTOs and network services. */
public record SphericalPathModel(String artifactVersion, Configuration configuration, List<Endpoint> endpoints) {
    public static final String VERSION = "sandbox-spherical-paths/v1";
    public record Configuration(String model, BigDecimal earthRadiusMeters, BigDecimal speedKph) {
        public Configuration {
            model = ContractChecks.version(model, "SPHERICAL_DIRECT/v1");
            earthRadiusMeters = ContractChecks.positive(earthRadiusMeters, "earthRadiusMeters");
            if (earthRadiusMeters.compareTo(new BigDecimal("6371000")) != 0)
                throw new IllegalArgumentException("v1 requires the specified earth radius");
            speedKph = ContractChecks.positive(speedKph, "speedKph");
            if (!Double.isFinite(speedKph.doubleValue()) || speedKph.doubleValue() / 3.6 <= 0)
                throw new IllegalArgumentException("Unrepresentable speed");
        }
    }
    public record Endpoint(String key, BigDecimal longitude, BigDecimal latitude) {
        public Endpoint {
            key = ContractChecks.text(key, "endpoint key");
            longitude = coordinate(longitude, 180, "longitude");
            latitude = coordinate(latitude, 90, "latitude");
        }
        private static BigDecimal coordinate(BigDecimal value, int limit, String field) {
            value = ContractChecks.decimal(value, field);
            if (value.abs().compareTo(BigDecimal.valueOf(limit)) > 0)
                throw new IllegalArgumentException("Invalid " + field);
            return value;
        }
    }
    public record Leg(String fromKey, String toKey, BigDecimal distanceMeters, long drivingSeconds) {}
    public SphericalPathModel {
        artifactVersion = ContractChecks.version(artifactVersion, VERSION);
        Objects.requireNonNull(configuration, "configuration");
        endpoints = ContractChecks.list(endpoints, "endpoints").stream()
                .sorted(Comparator.comparing(Endpoint::key)).toList();
        if (endpoints.stream().map(Endpoint::key).distinct().count() != endpoints.size())
            throw new IllegalArgumentException("Duplicate endpoint");
    }
    public Leg between(String fromKey, String toKey) {
        Endpoint from = require(fromKey), to = require(toKey);
        double lat1 = StrictMath.toRadians(from.latitude.doubleValue());
        double lat2 = StrictMath.toRadians(to.latitude.doubleValue());
        double dLat = lat2 - lat1;
        double dLon = StrictMath.toRadians(to.longitude.subtract(from.longitude).doubleValue());
        double a = StrictMath.pow(StrictMath.sin(dLat / 2), 2)
                + StrictMath.cos(lat1) * StrictMath.cos(lat2) * StrictMath.pow(StrictMath.sin(dLon / 2), 2);
        a = Math.max(0, Math.min(1, a));
        double distance = configuration.earthRadiusMeters.doubleValue() * 2
                * StrictMath.atan2(StrictMath.sqrt(a), StrictMath.sqrt(1 - a));
        double seconds = distance / (configuration.speedKph.doubleValue() / 3.6);
        if (!Double.isFinite(seconds) || seconds >= Long.MAX_VALUE)
            throw new IllegalArgumentException("Unrepresentable driving duration");
        return new Leg(fromKey, toKey, BigDecimal.valueOf(distance),
                distance == 0 ? 0 : Math.max(1, Math.round(seconds)));
    }
    private Endpoint require(String key) {
        return endpoints.stream().filter(e -> e.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown endpoint: " + key));
    }
    public String fingerprint() { return OptimizationJson.fingerprint(this); }
}
